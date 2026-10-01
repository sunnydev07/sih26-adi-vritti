package in.adivritti.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} backs {@code EligibilityService.invalidateRuleCache}. The
 * watch itself is inert unless {@code app.rules-watch-interval-ms} is non-zero, which
 * only the local dev override sets — enabling the scheduler costs one no-op tick in
 * production and buys a rules edit without a restart in development.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class AdiVrittiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdiVrittiApplication.class, args);
    }
}
