package in.adivritti.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationAttempt;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import in.adivritti.core.verification.strategy.VerificationStrategy.TierResult;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The contract's {@code idempotencyKey} must deduplicate verifications.
 *
 * <p>The field was accepted and documented ("repeated submissions for the same
 * key resolve to the same claim") but never read, so every retry minted
 * another wallet row. These tests pin the behaviour: the key is stored on the
 * claim, a repeat returns the same claim id without re-running the chain, and
 * keyless requests are untouched.
 */
class VerificationIdempotencyTest {

    private VerificationStrategy strategy;
    private ClaimRepository claims;
    private VerificationOrchestrator orchestrator;
    private final UUID usid = UUID.randomUUID();

    private static VerifyRequest request(String key) {
        return new VerifyRequest(UUID.randomUUID(), "income", "ev-1", key);
    }

    private static Claim existingClaim(UUID usid, String key) {
        Claim c = new Claim();
        c.id = UUID.randomUUID();
        c.usid = usid;
        c.claimType = "income";
        c.source = "gov_verified";
        c.method = "gov_verified";
        c.confidence = 0.9;
        c.verifiedAt = ZonedDateTime.now().minusDays(1);
        c.validUntil = ZonedDateTime.now().plusDays(364);
        c.idempotencyKey = key;
        c.valueEncrypted = new byte[] {1};
        return c;
    }

    @BeforeEach
    void setUp() {
        strategy = mock(VerificationStrategy.class);
        when(strategy.tier()).thenReturn("gov_verified");
        when(strategy.attempt(any(VerificationAttempt.class))).thenAnswer(invocation -> Optional.of(
            new TierResult(true, 0.9, "NSP", "deterministic", "note", null)));
        claims = mock(ClaimRepository.class);
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            if (c.id == null) c.id = UUID.randomUUID();
            return c;
        });
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            if (c.id == null) c.id = UUID.randomUUID();
            return c;
        });
        when(claims.findFirstByUsidAndIdempotencyKey(any(UUID.class), any(String.class)))
            .thenReturn(Optional.empty());
        ClaimValueCipher cipher = mock(ClaimValueCipher.class);
        when(cipher.seal(any(String.class))).thenReturn(new byte[] {1});
        orchestrator = new VerificationOrchestrator(List.of(strategy), claims,
            mock(DeficiencyRepository.class), cipher);
    }

    @Test
    @DisplayName("successful verification stores the caller key on the claim")
    void keyStoredOnClaim() {
        orchestrator.verify(new VerifyRequest(usid, "income", "ev-1", "key-1"));

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claims).saveAndFlush(captor.capture());
        assertThat(captor.getValue().idempotencyKey).isEqualTo("key-1");
        verify(claims, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("repeat with the same key returns the same claim without running the chain")
    void sameKeyReplays() {
        Claim existing = existingClaim(usid, "key-1");
        when(claims.findFirstByUsidAndIdempotencyKey(usid, "key-1"))
            .thenReturn(Optional.of(existing));

        VerifyResponse response =
            orchestrator.verify(new VerifyRequest(usid, "income", "ev-2", "key-1"));

        assertThat(response.claimId()).isEqualTo(existing.id);
        assertThat(response.verdict()).isEqualTo("verified");
        assertThat(response.provenance()).isEmpty();
        verify(strategy, never()).attempt(any(VerificationAttempt.class));
        verify(claims, never()).save(any(Claim.class));
        verify(claims, never()).saveAndFlush(any(Claim.class));
    }

    @Test
    @DisplayName("keyless requests skip the idempotency lookup and store no key")
    void noKeyUntouched() {
        orchestrator.verify(new VerifyRequest(usid, "income", "ev-1", null));

        verify(claims, never())
            .findFirstByUsidAndIdempotencyKey(any(UUID.class), any(String.class));
        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claims).save(captor.capture());
        assertThat(captor.getValue().idempotencyKey).isNull();
    }
}
