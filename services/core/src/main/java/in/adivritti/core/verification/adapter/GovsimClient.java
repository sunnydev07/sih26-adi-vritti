package in.adivritti.core.verification.adapter;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Shared govsim HTTP helper: GET {system}{path} with real resilience.
 *
 * <p>Every call runs inside a programmatic Resilience4j stack: up to 3
 * attempts with exponential backoff plus jitter for transient failures (5xx,
 * transport/IO errors, timeouts), wrapped in a circuit breaker that opens
 * after sustained failure so a down govsim degrades fast instead of stalling
 * every request on a 5s block. The programmatic style (no annotations) is deliberate: it
 * needs no AspectJ weaving on the classpath and stays unit-testable without
 * a Spring context.
 *
 * <p>When retries are exhausted, the breaker is open, or the body is empty,
 * the call falls back to {@code {available: false}} — the orchestrator
 * degrades to its next tier and never blocks.
 *
 * <p>The explicit bean name is load-bearing. Left implicit, this component's
 * default name is {@code govsimClient} (the decapitalised class name), which
 * collides with the {@code @Bean govsimClient} WebClient factory method in
 * {@code WebClientConfig}. Spring Boot 3 disables bean-definition overriding, so
 * the collision is not a warning: the context refresh fails with
 * {@code BeanDefinitionOverrideException} and the service refuses to start. The
 * unit suite never caught it because no test boots the application context.
 */
@Component("govsimAdapter")
public class GovsimClient {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient govsim;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public GovsimClient(@Qualifier("govsimClient") WebClient govsim) {
        this(govsim,
            Retry.of("govsim", RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(200, 2.0))
                .retryOnException(GovsimClient::isTransient)
                .build()),
            CircuitBreaker.of("govsim", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build()));
    }

    /** Package-visible so unit tests can inject tuned retry/breaker instances. */
    GovsimClient(WebClient govsim, Retry retry, CircuitBreaker circuitBreaker) {
        this.govsim = govsim;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Transient means worth retrying: 5xx, transport/IO failures, timeouts.
     * 4xx is a terminal answer from the mock, and an open breaker must fail
     * fast rather than burn through its retry budget sleeping.
     */
    static boolean isTransient(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return false;
        }
        if (t instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path) {
        try {
            Supplier<Map<String, Object>> fetch = () -> Objects.requireNonNull(
                govsim.get().uri(path).retrieve()
                    .bodyToMono(Map.class).block(BLOCK_TIMEOUT),
                "empty govsim body");
            // Nesting is load-bearing: the breaker decorates first so it sits
            // closest to the call and every retry attempt passes through it
            // (retry outermost, breaker innermost).
            Supplier<Map<String, Object>> guarded = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, fetch));
            return guarded.get();
        } catch (Exception e) {
            // Source unavailable -> orchestrator degrades to next tier, never blocks.
            // String.valueOf (not e.getMessage() directly): Map.of rejects null
            // values, and several failure modes carry a null message.
            return Map.of("available", false, "error", String.valueOf(e.getMessage()));
        }
    }
}
