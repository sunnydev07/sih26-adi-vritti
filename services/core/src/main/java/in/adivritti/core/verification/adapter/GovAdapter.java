package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;

/**
 * GovSim adapter. Core treats govsim EXACTLY like real government APIs:
 * timeouts, circuit breakers (Resilience4j), retries with jitter at call site.
 */
public interface GovAdapter {
    String name();
    boolean supports(String claimType);
    CheckResult check(VerifyRequest req);

    record CheckResult(boolean verified, double confidence, String note) {}
}
