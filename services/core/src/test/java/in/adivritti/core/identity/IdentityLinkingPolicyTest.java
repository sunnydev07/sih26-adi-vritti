package in.adivritti.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.common.util.AadhaarVault;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.entity.ScholarSystemLink;
import in.adivritti.core.identity.repository.IdentityResolutionRepository;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.identity.repository.ScholarSystemLinkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

/**
 * What a resolution is allowed to write.
 *
 * <p>The resolver scored every pair and reported every comparison, but persisting was
 * unconditional: a 0.0 score still produced a real {@code ScholarSystemLink} row, the
 * review band (0.60–0.90) was linked <em>before</em> any officer saw it, and a
 * re-presented record with a known Aadhaar minted a second USID instead of reusing the
 * first. These tests pin the policy: deterministic and above-ceiling matches link,
 * everything else is reported-not-written; the anchor reuses the Aadhaar-known USID;
 * conflicting USIDs are flagged, never merged.
 */
class IdentityLinkingPolicyTest {

    private static final String AADHAAR_REF =
        "AVR1:v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private UsidResolver resolver;
    private ScholarRepository scholars;
    private ScholarSystemLinkRepository links;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        resolver = mock(UsidResolver.class);
        scholars = mock(ScholarRepository.class);
        links = mock(ScholarSystemLinkRepository.class);
        service = new IdentityService(resolver, scholars, links,
            mock(IdentityResolutionRepository.class), mock(AadhaarVault.class));

        // The review band mirrors the real thresholds, so these tests exercise the
        // link-vs-queue policy rather than the thresholds themselves.
        when(resolver.needsHumanReview(anyDouble())).thenAnswer(invocation -> {
            double score = invocation.getArgument(0);
            return score >= UsidResolver.REVIEW_FLOOR && score < UsidResolver.REVIEW_CEILING;
        });
        when(links.findBySystemNameAndExternalId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(links.findFirstBySystemNameAndExternalIdOrderByLinkedAtDesc(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(scholars.findByAadhaarRefKey(anyString())).thenReturn(List.of());
        when(scholars.save(any(Scholar.class))).thenAnswer(invocation -> {
            Scholar scholar = invocation.getArgument(0);
            if (scholar.usid == null) {
                scholar.usid = UUID.randomUUID();
            }
            return scholar;
        });
    }

    private static IdentityRecord rec(String system, String externalId, String refKey) {
        return IdentityRecord.of(system, externalId, "Sunita Meena", "2012-05-01", "female",
            "Soma Ram", "SCH-0001", "Mandla", "1234", null, refKey);
    }

    private static IdentityResolveRequest request(IdentityRecord... records) {
        return new IdentityResolveRequest(List.of(records), null);
    }

    private static ArgumentMatcher<ScholarSystemLink> linkFor(String externalId) {
        return link -> externalId.equals(link.externalId);
    }

    private static ScholarSystemLink linkForUsid(UUID usid) {
        ScholarSystemLink link = new ScholarSystemLink();
        link.usid = usid;
        return link;
    }

    @Test
    @DisplayName("below the floor the batch is reported and queued, never linked")
    void belowFloorScoreIsReportedQueuedButNotLinked() {
        when(resolver.deterministicMatch(any(), any())).thenReturn(false);
        when(resolver.score(any(), any())).thenReturn(0.0);

        IdentityResolveResponse response =
            service.resolve(request(rec("NSP", "NSP-1", null), rec("SFMP", "SFMP-9", null)));

        // A batch that disagreed with itself needs a human even though no single pair
        // landed in the review band.
        assertThat(response.linkedRecords()).hasSize(1);
        assertThat(response.linkedRecords().get(0).confidence()).isEqualTo(0.0);
        assertThat(response.linkedRecords().get(0).resolutionMethod()).isEqualTo("probabilistic");
        assertThat(response.needsHumanReview()).isTrue();
        // One mint, one write — the idempotency anchor — and the 0.0 record gets none.
        assertThat(response.usid()).isNotNull();
        verify(scholars, times(1)).save(any(Scholar.class));
        verify(links, times(1)).save(any(ScholarSystemLink.class));
        verify(links, never())
            .save(argThat((ScholarSystemLink link) -> "SFMP-9".equals(link.externalId)));
    }

    @Test
    @DisplayName("a review-band score is queued for adjudication, not linked")
    void reviewBandScoreIsQueuedNotLinked() {
        when(resolver.deterministicMatch(any(), any())).thenReturn(false);
        when(resolver.score(any(), any())).thenReturn(0.75);

        IdentityResolveResponse response =
            service.resolve(request(rec("NSP", "NSP-1", null), rec("SFMP", "SFMP-9", null)));

        assertThat(response.needsHumanReview()).isTrue();
        assertThat(response.linkedRecords().get(0).resolutionMethod())
            .isEqualTo("human_adjudication");
        // The anchor row is the only write: the review-band record must not follow it.
        verify(links, times(1)).save(any(ScholarSystemLink.class));
        verify(links, never())
            .save(argThat((ScholarSystemLink link) -> "SFMP-9".equals(link.externalId)));
    }

    @Test
    @DisplayName("deterministic and above-ceiling matches both link")
    void confidentMatchesAreLinked() {
        when(resolver.score(any(), any())).thenReturn(0.95);
        when(resolver.deterministicMatch(any(), any()))
            .thenAnswer(invocation -> {
                IdentityRecord first = invocation.getArgument(0);
                IdentityRecord second = invocation.getArgument(1);
                return "DETERM".equals(second.externalId())
                    || "NSP-1".equals(first.externalId()) && "DETERM".equals(second.externalId());
            });

        IdentityResolveResponse response = service.resolve(request(
            rec("NSP", "NSP-1", null), rec("SFMP", "DETERM", null), rec("NOS", "NOS-3", null)));

        assertThat(response.needsHumanReview()).isFalse();
        verify(links).save(argThat(linkFor("NOS-3")));
        verify(links).save(argThat(linkFor("DETERM")));
    }

    @Test
    @DisplayName("a re-presented record with a known Aadhaar reuses the USID")
    void knownAadhaarReusesTheExistingUsid() {
        UUID knownUsid = UUID.randomUUID();
        Scholar known = new Scholar();
        known.usid = knownUsid;
        when(scholars.findByAadhaarRefKey(AADHAAR_REF)).thenReturn(List.of(known));

        IdentityResolveResponse response = service.resolve(request(
            rec("UDISE", "UDI-NEW", AADHAAR_REF), rec("NOS", "NOS-7", AADHAAR_REF)));

        assertThat(response.usid()).isEqualTo(knownUsid);
        assertThat(response.duplicateFlag().isDuplicate()).isFalse();
        verify(scholars, never()).save(any(Scholar.class));
    }

    @Test
    @DisplayName("records pointing at different USIDs are flagged, never merged")
    void conflictingUsidsAreFlagged() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Scholar other = new Scholar();
        other.usid = second;
        when(links.findFirstBySystemNameAndExternalIdOrderByLinkedAtDesc(anyString(), anyString()))
            .thenAnswer(invocation -> {
                String externalId = invocation.getArgument(1);
                if ("NSP-1".equals(externalId)) {
                    return Optional.of(linkForUsid(first));
                }
                return Optional.empty();
            });
        when(scholars.findByAadhaarRefKey(AADHAAR_REF)).thenReturn(List.of(other));

        IdentityResolveResponse response = service.resolve(request(
            rec("NSP", "NSP-1", AADHAAR_REF), rec("SFMP", "SFMP-9", AADHAAR_REF)));

        assertThat(response.duplicateFlag().isDuplicate()).isTrue();
        assertThat(response.duplicateFlag().duplicateUsids()).containsExactlyInAnyOrder(first, second);
        assertThat(response.needsHumanReview()).isTrue();
        verify(scholars, never()).save(any(Scholar.class));
        verify(links, never()).save(any(ScholarSystemLink.class));
    }

    @Test
    @DisplayName("a genuinely new person mints exactly one scholar and its anchor link")
    void newPersonMintsOnce() {
        when(resolver.deterministicMatch(any(), any())).thenReturn(true);

        IdentityResolveResponse response =
            service.resolve(request(rec("NSP", "NSP-NEW", null), rec("SFMP", "SFMP-NEW", null)));

        verify(scholars, times(1)).save(any(Scholar.class));
        assertThat(response.usid()).isNotNull();
        assertThat(response.duplicateFlag().isDuplicate()).isFalse();
        verify(links).save(argThat((ScholarSystemLink link) -> "NSP-NEW".equals(link.externalId)
            && response.usid().equals(link.usid)));
        verify(links).save(argThat(linkFor("SFMP-NEW")));
    }
}
