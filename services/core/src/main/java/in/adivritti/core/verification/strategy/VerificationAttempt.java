package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.adapter.GovAdapter;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One verification attempt: the request plus a memo of government-system probes.
 *
 * <p>The tiered chain asks every adapter the same question, so without a memo the
 * authoritative tier and the corroboration tier each fired their own HTTP call. The
 * memo makes {@link #probe(List)} return a cached result for the second caller, and
 * guarantees both tiers see identical data even if a flaky portal answers differently
 * on a retry.
 */
public final class VerificationAttempt {

    private final VerifyRequest request;
    private final Map<String, GovAdapter.CheckResult> memo = new LinkedHashMap<>();

    public VerificationAttempt(VerifyRequest request) {
        this.request = request;
    }

    public VerifyRequest request() {
        return request;
    }

    /** Ask each supporting adapter exactly once per attempt, caching the answers. */
    public List<GovAdapter.CheckResult> probe(List<GovAdapter> adapters) {
        List<GovAdapter.CheckResult> results = new ArrayList<>();
        for (GovAdapter adapter : adapters) {
            if (!adapter.supports(request.claimType())) continue;
            results.add(memo.computeIfAbsent(adapter.name(), name -> {
                try {
                    return adapter.check(request);
                } catch (RuntimeException e) {
                    // An unreachable portal is not a verification failure; it means
                    // "unknown", which the next tier may still resolve.
                    return new GovAdapter.CheckResult(false, 0.0,
                        adapter.name() + " unavailable: " + e.getClass().getSimpleName());
                }
            }));
        }
        return results;
    }

    /** How many independent systems affirm the claim. */
    public long agreeing(List<GovAdapter> adapters) {
        return probe(adapters).stream().filter(GovAdapter.CheckResult::verified).count();
    }
}
