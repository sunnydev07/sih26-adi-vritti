package in.adivritti.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationAttempt;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import in.adivritti.core.verification.strategy.VerificationStrategy.TierResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A tier that verifies with an adapter-sourced value seals that value into the
 * wallet; a tier without one keeps the {@code VALUELESS} marker. The request
 * itself carries no value — callers can never invent a number.
 */
class VerificationValuePersistenceTest {

    private VerificationOrchestrator orchestrator(String tierValue, ClaimValueCipher cipher,
        ClaimRepository claims) {
        VerificationStrategy strategy = mock(VerificationStrategy.class);
        when(strategy.tier()).thenReturn("gov_verified");
        when(strategy.attempt(any(VerificationAttempt.class))).thenAnswer(invocation ->
            Optional.of(new TierResult(true, 0.99, "government-api", "api", "note", tierValue)));
        return new VerificationOrchestrator(List.of(strategy), claims,
            mock(DeficiencyRepository.class), cipher);
    }

    private ClaimRepository savingClaims() {
        ClaimRepository claims = mock(ClaimRepository.class);
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            if (c.id == null) c.id = UUID.randomUUID();
            return c;
        });
        return claims;
    }

    private ClaimValueCipher echoCipher() {
        ClaimValueCipher cipher = mock(ClaimValueCipher.class);
        when(cipher.seal(any(String.class))).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    @Test
    @DisplayName("adapter value is sealed into the wallet claim")
    void adapterValuePersisted() {
        ClaimRepository claims = savingClaims();
        VerificationOrchestrator orchestrator =
            orchestrator("24000000", echoCipher(), claims);

        orchestrator.verify(new VerifyRequest(UUID.randomUUID(), "income", "ev-1", null));

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        org.mockito.Mockito.verify(claims).save(saved.capture());
        assertThat(new String(saved.getValue().valueEncrypted, StandardCharsets.UTF_8))
            .isEqualTo("24000000");
    }

    @Test
    @DisplayName("tier without a value keeps the VALUELESS marker")
    void missingValueStaysValueless() {
        ClaimRepository claims = savingClaims();
        VerificationOrchestrator orchestrator = orchestrator(null, echoCipher(), claims);

        orchestrator.verify(new VerifyRequest(UUID.randomUUID(), "income", "ev-1", null));

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        org.mockito.Mockito.verify(claims).save(saved.capture());
        assertThat(new String(saved.getValue().valueEncrypted, StandardCharsets.UTF_8))
            .isEqualTo(VerificationOrchestrator.VALUELESS);
    }
}
