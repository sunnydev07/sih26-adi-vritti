package in.adivritti.core.config;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Outbound HTTP clients.
 *
 * <p>Core talks to govsim and the AI service over {@code WebClient}, which is
 * non-blocking. The previous configuration set no timeouts and no connection pool,
 * so a hung government endpoint held a request open until the container gave up.
 * Timeouts are explicit here, and a bounded reactor-netty pool stops a slow
 * downstream from exhausting file descriptors.
 *
 * <p>The AI service rejects every route except /health without the shared service
 * token, so the client presents it. It used to present nothing, which made the
 * whole Tier-3 assisted verification path fail on 401 and look like a document
 * nobody could read.
 */
@Configuration
public class WebClientConfig {

    @Bean
    WebClient govsimClient(
        @Value("${app.govsim.base-url:http://localhost:4000}") String baseUrl,
        @Value("${app.govsim.connect-timeout-seconds:2}") long connectSeconds,
        @Value("${app.govsim.read-timeout-seconds:5}") long readSeconds) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(connector(Duration.ofSeconds(connectSeconds),
                Duration.ofSeconds(readSeconds)))
            .build();
    }

    @Bean
    WebClient aiServiceClient(
        @Value("${app.ai-service.base-url:http://localhost:8000}") String baseUrl,
        @Value("${app.ai-service.connect-timeout-seconds:2}") long connectSeconds,
        @Value("${app.ai-service.read-timeout-seconds:20}") long readSeconds,
        @Value("${app.ai-service.token:}") String token) {
        WebClient.Builder builder = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(connector(Duration.ofSeconds(connectSeconds),
                Duration.ofSeconds(readSeconds)));
        if (token != null && !token.isBlank()) {
            builder.filter(aiServiceAuth(token, baseUrl));
        }
        return builder.build();
    }

    /**
     * Presents the shared service token as a bearer credential.
     *
     * <p>Scoped to the AI service host on purpose rather than set as a blanket
     * default header: this client is also used to FETCH evidence, and an
     * evidenceRef is a caller-supplied URL, so an unconditional header would hand
     * the service credential to whichever host the caller named.
     */
    private static ExchangeFilterFunction aiServiceAuth(String token, String baseUrl) {
        URI ai = URI.create(baseUrl);
        return (request, next) -> {
            if (!targets(request.url(), ai)) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request)
                .headers(headers -> headers.setBearerAuth(token))
                .build());
        };
    }

    private static boolean targets(URI target, URI ai) {
        return target != null && target.getHost() != null
            && target.getHost().equalsIgnoreCase(ai.getHost())
            && port(target) == port(ai);
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** Bounded reactor-netty connector with explicit connect and response timeouts. */
    private static ReactorClientHttpConnector connector(Duration connectTimeout,
        Duration responseTimeout) {
        HttpClient httpClient = HttpClient.create()
            .followRedirect(false)
            .compress(true)
            .responseTimeout(responseTimeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());
        return new ReactorClientHttpConnector(httpClient);
    }
}
