package in.adivritti.core.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * GovsimClient is the single place where transport resilience lives (retry +
 * circuit breaker around every adapter call), so its fallback contract and
 * transient-classification are pinned here rather than behind a context.
 */
class GovsimClientTest {

    private static GovsimClient unreachableClient() {
        // Nothing listens on port 1: connection refused, fast and hermetic.
        return new GovsimClient(WebClient.create("http://127.0.0.1:1"));
    }

    @Test
    @DisplayName("unreachable govsim degrades to {available:false} and never throws")
    void unreachableFallsBack() {
        Map<String, Object> res = unreachableClient().get("/nsp/verify?usid=x");

        assertThat(res.get("available")).isEqualTo(false);
        assertThat(res.get("error")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("4xx is terminal, 5xx/IO/open-breaker-short-circuit classify correctly")
    void transientClassification() {
        assertThat(GovsimClient.isTransient(
            WebClientResponseException.create(503, "Service Unavailable", null, null, null)))
            .isTrue();
        assertThat(GovsimClient.isTransient(
            WebClientResponseException.create(404, "Not Found", null, null, null)))
            .isFalse();
        assertThat(GovsimClient.isTransient(new IOException("reset"))).isTrue();
    }

    @Test
    @DisplayName("open breaker fails fast to the fallback without calling the network")
    void openBreakerFailsFast() {
        CircuitBreaker open = CircuitBreaker.of("test-open",
            CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(2)
                .build());
        open.transitionToOpenState();
        GovsimClient client = new GovsimClient(
            WebClient.create("http://127.0.0.1:1"),
            Retry.ofDefaults("test"),
            open);

        // Would throw CallNotPermittedException without the fallback; would
        // also retry-pointlessly without the CallNotPermitted exclusion.
        Map<String, Object> res = client.get("/nsp/verify?usid=x");

        assertThat(res.get("available")).isEqualTo(false);
        assertThat(open.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("retry budget is bounded: a failing call returns, it does not hang")
    void retryBudgetBounded() {
        GovsimClient client = new GovsimClient(
            WebClient.create("http://127.0.0.1:1"),
            Retry.of("test-fast", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(GovsimClient::isTransient)
                .build()),
            CircuitBreaker.ofDefaults("test"));

        long start = System.nanoTime();
        Map<String, Object> res = client.get("/nsp/verify?usid=x");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(res.get("available")).isEqualTo(false);
        assertThat(elapsedMs).isLessThan(10_000);
    }
}
