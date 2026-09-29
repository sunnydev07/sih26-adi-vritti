package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Shared govsim HTTP helper: GET {system}{path} with sane timeouts.
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
    private final WebClient govsim;

    public GovsimClient(@Qualifier("govsimClient") WebClient govsim) {
        this.govsim = govsim;
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> get(String path) {
        try {
            return govsim.get().uri(path).retrieve()
                .bodyToMono(java.util.Map.class).block(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            // Source unavailable -> orchestrator degrades to next tier, never blocks.
            return java.util.Map.of("available", false, "error", e.getMessage());
        }
    }
}
