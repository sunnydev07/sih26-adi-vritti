package in.adivritti.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.common.util.AadhaarVault;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.entity.IdentityResolution;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.repository.IdentityResolutionRepository;
import in.adivritti.core.identity.repository.ScholarRepository;
import in.adivritti.core.identity.repository.ScholarSystemLinkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The contract's {@code idempotencyKey} must actually deduplicate.
 *
 * <p>The field was accepted and documented ("a repeat of the same resolution
 * returns the original USID instead of minting a second one") but never read,
 * so a retried submission for a genuinely new person minted a second scholar.
 * These tests pin the freeze-and-replay behaviour: same key, same response,
 * one mint.
 */
class IdentityIdempotencyTest {

    private UsidResolver resolver;
    private ScholarRepository scholars;
    private ScholarSystemLinkRepository links;
    private IdentityResolutionRepository resolutions;
    private IdentityService service;
    private final AtomicReference<IdentityResolution> stored = new AtomicReference<>();

    private static IdentityResolveRequest request(String key) {
        IdentityRecord record = IdentityRecord.of(
            "NSP", "NSP-7", "Sunita Meena", "2012-05-01", "female",
            null, null, "Mandla", null, "12345678901234", null);
        return new IdentityResolveRequest(List.of(record), key);
    }

    @BeforeEach
    void setUp() {
        resolver = mock(UsidResolver.class);
        scholars = mock(ScholarRepository.class);
        links = mock(ScholarSystemLinkRepository.class);
        resolutions = mock(IdentityResolutionRepository.class);
        service = new IdentityService(resolver, scholars, links, resolutions,
            mock(AadhaarVault.class));

        // A genuinely new person: no links, no known Aadhaar, deterministic match.
        when(links.findFirstBySystemNameAndExternalIdOrderByLinkedAtDesc(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(links.findBySystemNameAndExternalId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(scholars.findByAadhaarRefKey(anyString())).thenReturn(List.of());
        when(resolver.deterministicMatch(any(), any())).thenReturn(true);
        when(scholars.save(any(Scholar.class))).thenAnswer(invocation -> {
            Scholar s = invocation.getArgument(0);
            if (s.usid == null) s.usid = UUID.randomUUID();
            return s;
        });
        // In-memory stand-in for the unique-keyed table.
        when(resolutions.findByIdempotencyKey(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(resolutions.saveAndFlush(any(IdentityResolution.class))).thenAnswer(invocation -> {
            IdentityResolution row = invocation.getArgument(0);
            stored.set(row);
            return row;
        });
    }

    @Test
    @DisplayName("repeat submission with the same key returns the original USID without re-minting")
    void sameKeyReplays() {
        IdentityResolveResponse first = service.resolve(request("key-1"));
        IdentityResolveResponse second = service.resolve(request("key-1"));

        assertThat(second.usid()).isEqualTo(first.usid());
        assertThat(second.linkedRecords()).isEqualTo(first.linkedRecords());
        assertThat(second.overallConfidence()).isEqualTo(first.overallConfidence());
        verify(scholars, times(1)).save(any(Scholar.class));
    }

    @Test
    @DisplayName("different keys resolve independently")
    void differentKeysResolveIndependently() {
        IdentityResolveResponse first = service.resolve(request("key-1"));

        stored.set(null);
        when(resolutions.findByIdempotencyKey(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        IdentityResolveResponse second = service.resolve(request("key-2"));

        assertThat(second.usid()).isNotEqualTo(first.usid());
        verify(scholars, times(2)).save(any(Scholar.class));
    }

    @Test
    @DisplayName("no key means no idempotency row is touched")
    void noKeyNoRow() {
        service.resolve(request(null));

        verify(resolutions, never()).findByIdempotencyKey(anyString());
        verify(resolutions, never()).saveAndFlush(any(IdentityResolution.class));
    }
}
