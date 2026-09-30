package in.adivritti.core.verification.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.adivritti.core.verification.adapter.GovAdapter;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VerificationAttemptTest {

    private static final VerifyRequest REQ = new VerifyRequest(
        UUID.fromString("11111111-1111-1111-1111-111111111111"), "income", null, null);

    /** Counts calls so we can prove the memo prevents duplicate HTTP round-trips. */
    private static final class CountingAdapter implements GovAdapter {
        private final String name;
        private final boolean verified;
        private final AtomicInteger calls = new AtomicInteger();
        private final RuntimeException failure;

        CountingAdapter(String name, boolean verified) {
            this(name, verified, null);
        }

        CountingAdapter(String name, boolean verified, RuntimeException failure) {
            this.name = name;
            this.verified = verified;
            this.failure = failure;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean supports(String claimType) {
            return true;
        }

        @Override
        public CheckResult check(VerifyRequest req) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            return new CheckResult(verified, verified ? 0.9 : 0.0, name + " lookup", null);
        }
    }

    @Test
    void eachAdapterIsProbedOnlyOncePerAttempt() {
        // The authoritative and corroboration tiers both used to call check()
        // independently, doubling the HTTP cost of every verification.
        CountingAdapter a = new CountingAdapter("NSP", true);
        CountingAdapter b = new CountingAdapter("SFMP", false);
        VerificationAttempt attempt = new VerificationAttempt(REQ);

        attempt.probe(List.of(a, b));
        attempt.probe(List.of(a, b));
        attempt.agreeing(List.of(a, b));

        assertEquals(1, a.calls.get());
        assertEquals(1, b.calls.get());
    }

    @Test
    void unsupportedClaimTypesAreNotProbed() {
        GovAdapter onlyOther = new GovAdapter() {
            @Override
            public String name() {
                return "NOS";
            }

            @Override
            public boolean supports(String claimType) {
                return false;
            }

            @Override
            public CheckResult check(VerifyRequest req) {
                throw new AssertionError("must not be called");
            }
        };
        assertTrue(new VerificationAttempt(REQ).probe(List.of(onlyOther)).isEmpty());
    }

    @Test
    void bothTiersSeeIdenticalResultsFromTheMemo() {
        CountingAdapter a = new CountingAdapter("NSP", true);
        CountingAdapter b = new CountingAdapter("SFMP", true);
        VerificationAttempt attempt = new VerificationAttempt(REQ);

        long fromProbe = attempt.probe(List.of(a, b)).stream()
            .filter(GovAdapter.CheckResult::verified).count();
        long fromAgreeing = attempt.agreeing(List.of(a, b));

        assertEquals(fromProbe, fromAgreeing);
        assertEquals(2, fromAgreeing, "two agreeing systems should corroborate");
    }

    @Test
    void anUnreachableAdapterIsTreatedAsUnknownNotAsAFailure() {
        // A portal being down must not be recorded as a definitive "not verified".
        CountingAdapter broken = new CountingAdapter("PFMS", true,
            new IllegalStateException("connection refused"));
        var results = new VerificationAttempt(REQ).probe(List.of(broken));

        assertEquals(1, results.size());
        assertEquals(false, results.get(0).verified());
        assertTrue(results.get(0).note().contains("unavailable"), results.get(0).note());
    }
}
