package in.adivritti.core.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DigiLocker check results carry the issued-document field answering the claim
 * type, so the wallet stores adapter-sourced values. Claim types with no rule
 * key verify without a value rather than guessing one.
 */
class DigiLockerAdapterTest {

    private static VerifyRequest request(String claimType) {
        return new VerifyRequest(UUID.randomUUID(), claimType, "ev-1", null);
    }

    private static DigiLockerAdapter adapter(Map<String, Object> payload) {
        GovsimClient govsim = mock(GovsimClient.class);
        when(govsim.get(anyString())).thenReturn(payload);
        return new DigiLockerAdapter(govsim);
    }

    private static Map<String, Object> success() {
        return Map.of("verified", true, "system", "DigiLocker-proxy", "fields", Map.of(
            "family_income_annual_paise", 24000000,
            "st_or_pvtg_status", "ST",
            "class_level", 9));
    }

    @Test
    @DisplayName("income check carries the paise value")
    void incomeCarriesValue() {
        GovAdapter.CheckResult r = adapter(success()).check(request("income"));
        assertThat(r.verified()).isTrue();
        assertThat(r.value()).isEqualTo("24000000");
    }

    @Test
    @DisplayName("st_status check carries the community value")
    void stStatusCarriesValue() {
        GovAdapter.CheckResult r = adapter(success()).check(request("st_status"));
        assertThat(r.verified()).isTrue();
        assertThat(r.value()).isEqualTo("ST");
    }

    @Test
    @DisplayName("academic check carries the class level")
    void academicCarriesValue() {
        GovAdapter.CheckResult r = adapter(success()).check(request("academic"));
        assertThat(r.verified()).isTrue();
        assertThat(r.value()).isEqualTo("9");
    }

    @Test
    @DisplayName("claim types with no rule key verify without a value")
    void unmappedClaimTypesStayValueless() {
        assertThat(adapter(success()).check(request("domicile")).value()).isNull();
        assertThat(adapter(success()).check(request("identity")).value()).isNull();
    }

    @Test
    @DisplayName("missing fields payload verifies without a value")
    void missingFieldsStayValueless() {
        GovAdapter.CheckResult r = adapter(Map.of("verified", true)).check(request("income"));
        assertThat(r.verified()).isTrue();
        assertThat(r.value()).isNull();
    }

    @Test
    @DisplayName("rejection carries no value")
    void rejectionCarriesNoValue() {
        GovAdapter.CheckResult r = adapter(
            Map.of("verified", false, "reason_code", "DOCUMENT_MISMATCH"))
            .check(request("income"));
        assertThat(r.verified()).isFalse();
        assertThat(r.value()).isNull();
    }
}
