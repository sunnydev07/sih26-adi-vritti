package in.adivritti.core.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for privacy decisions.
 *
 * <p>{@link in.adivritti.core.consent.ConsentGate} decides whether a scholar is a
 * minor from their date of birth, and a wrong answer there is a DPDP breach rather
 * than a cosmetic bug. It used to call {@code LocalDate.now()} directly, which meant
 * the answer could not be pinned in a test: a fixture born in 2008 asserted
 * "is a minor" correctly for eighteen years and then silently started taking the
 * adult path. Injecting the clock moves the boundary under the test's control.
 *
 * <p>UTC, matching the JDBC timezone in {@code application.yml}, so a request served
 * by a node in a different zone cannot compute a different age than the node that
 * wrote the access-audit row for it.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
