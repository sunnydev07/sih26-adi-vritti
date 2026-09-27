package in.adivritti.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient govsimClient(@Value("${app.govsim.base-url:http://localhost:4000}") String baseUrl) {
        // Core treats govsim EXACTLY like real government APIs (timeouts, retries at call site).
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    WebClient aiServiceClient(@Value("${app.ai-service.base-url:http://localhost:8000}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
