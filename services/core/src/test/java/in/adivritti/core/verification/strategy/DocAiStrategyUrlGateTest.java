package in.adivritti.core.verification.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code evidenceRef} is caller-supplied and fetched server-side, so the URL
 * gate is the SSRF boundary. These tests pin it as a pure function: no network,
 * no Spring, deterministic on any machine.
 *
 * <p>DNS-dependent cases use the {@code .invalid} TLD (RFC 2606, never resolves)
 * or IP literals (no lookup at all), so results cannot vary with the resolver.
 */
class DocAiStrategyUrlGateTest {

    private static final Set<String> NONE = Set.of();
    private static final Set<String> ALLOW_DEPT = Set.of("dept.invalid");

    @ParameterizedTest(name = "rejected: {0}")
    @ValueSource(strings = {
        "",
        "   ",
        "not-a-url",
        "ftp://files.example/file.pdf",
        "file:///etc/passwd",
        "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
        "http://127.0.0.1:5432/",
        "http://localhost:9000/doc.pdf",
        "http://LOCALHOST/doc.pdf",
        "http://[::1]/",
        "http://0.0.0.0/",
        "http://user:pass@docs.dept.invalid/f",
        "http://192.168.1.10/internal",
        "http://10.0.0.5/x",
        "http://[fd00::1]/x",
        "https://evildept.invalid/f",
        "https://definitely-not-a-real-host.invalid/f",
    })
    void hostileOrMalformedUrlsAreRejected(String ref) {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(ref, NONE)).isFalse();
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(ref, ALLOW_DEPT)).isFalse();
    }

    @Test
    @DisplayName("null is rejected without throwing")
    void nullIsRejected() {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(null, NONE)).isFalse();
    }

    @Test
    @DisplayName("allow-listed host and its subdomains pass with no DNS lookup")
    void allowListedHostsPass() {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "https://docs.dept.invalid/f/1", ALLOW_DEPT)).isTrue();
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "https://dept.invalid/", ALLOW_DEPT)).isTrue();
        // Case and trailing dot are normalised before matching.
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "https://DOCS.DEPT.INVALID./f", ALLOW_DEPT)).isTrue();
    }

    @Test
    @DisplayName("dot-boundary matching: evil-dept.invalid is not dept.invalid")
    void suffixMatchRequiresADotBoundary() {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "https://evildept.invalid/f", ALLOW_DEPT)).isFalse();
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "https://dept.invalid.evildept.invalid/f", ALLOW_DEPT)).isFalse();
    }

    @Test
    @DisplayName("site-local addresses pass only via the allow-list")
    void siteLocalNeedsAllowListing() {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl("http://192.168.1.10/x", NONE)).isFalse();
        assertThat(DocAiStrategy.isAllowedEvidenceUrl(
            "http://192.168.1.10/x", Set.of("192.168.1.10"))).isTrue();
    }

    @Test
    @DisplayName("public IP literals pass (no DNS involved)")
    void publicIpLiteralsPass() {
        assertThat(DocAiStrategy.isAllowedEvidenceUrl("https://8.8.8.8/f", NONE)).isTrue();
    }

    @ParameterizedTest(name = "confidence {0} is unreadable")
    @ValueSource(doubles = {Double.NaN, -0.5, -0.0 - 1e-9, 1.000001, 42.0, 95.0,
        Double.POSITIVE_INFINITY})
    void outOfRangeConfidenceIsRejected(double value) {
        assertThat(DocAiStrategy.validConfidence(value)).isNull();
    }

    @ParameterizedTest(name = "confidence {0} is accepted")
    @ValueSource(doubles = {0.0, 0.6, 0.95, 1.0})
    void inRangeConfidenceIsAccepted(double value) {
        assertThat(DocAiStrategy.validConfidence(value)).isEqualTo(value);
    }

    @Test
    @DisplayName("non-numeric confidence is unreadable")
    void nonNumericConfidenceIsRejected() {
        assertThat(DocAiStrategy.validConfidence(null)).isNull();
        assertThat(DocAiStrategy.validConfidence("0.95")).isNull();
    }
}
