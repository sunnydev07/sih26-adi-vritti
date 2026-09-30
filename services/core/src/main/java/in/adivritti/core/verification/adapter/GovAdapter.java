package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;

/**
 * GovSim adapter. Core treats govsim EXACTLY like real government APIs:
 * timeouts, retries with backoff and a circuit breaker live in
 * {@link GovsimClient} (programmatic Resilience4j, shared by every adapter);
 * adapters only map responses to verdicts and degrade — never block — when a
 * source is down.
 */
public interface GovAdapter {
    String name();
    boolean supports(String claimType);
    CheckResult check(VerifyRequest req);

    /**
     * @param value an adapter-sourced field value for the wallet (e.g. a DigiLocker
     *     document field), or {@code null} when the source carries no value. Only
     *     adapter-derived values are ever persisted — the contract's
     *     {@code VerifyRequest} carries no value, so callers cannot invent one.
     */
    record CheckResult(boolean verified, double confidence, String note, String value) {}
}
