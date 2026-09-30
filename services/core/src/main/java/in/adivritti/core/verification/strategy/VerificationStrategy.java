package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Optional;

/** One tier of the 4-tier strategy chain. Returns empty when it cannot decide. */
public interface VerificationStrategy {
    String tier();
    Optional<TierResult> attempt(VerificationAttempt attempt);

    /**
     * @param value an adapter-sourced field value for the wallet, or {@code null}
     *     when the tier verifies without a value (corroboration, doc-AI,
     *     manual review). Only Tier 1 carries adapter values today.
     */
    record TierResult(boolean verified, double confidence, String source, String method, String note,
        String value) {}

    /** Convenience for strategies that do not need the request itself. */
    default Optional<TierResult> attempt(VerifyRequest request) {
        return attempt(new VerificationAttempt(request));
    }
}
