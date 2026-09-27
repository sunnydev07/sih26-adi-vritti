package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Optional;

/** One tier of the 4-tier strategy chain. Returns empty when it cannot decide. */
public interface VerificationStrategy {
    String tier();
    Optional<TierResult> attempt(VerifyRequest req);

    record TierResult(boolean verified, double confidence, String source, String method, String note) {}
}
