package in.adivritti.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse.ProvenanceRecord;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * The Redis value serializer must round-trip what the verification cache actually
 * stores.
 *
 * <p>The previous serializer was {@code new GenericJackson2JsonRedisSerializer()},
 * whose internal {@code ObjectMapper} has no Java time module: the first
 * {@code validUntil} threw on {@code put}, the {@code CacheErrorHandler} logged it as
 * an outage, and every verification was a silent cache miss. This test pins the two
 * properties that fix needs: timestamps survive, and record type hints survive (a
 * read that returns {@code LinkedHashMap} fails at the call site instead).
 *
 * <p>This is the wire half only; {@code VerificationCacheBehaviourTest} covers the
 * cache key and the {@code unless} clause without Redis.
 */
class CacheConfigSerializationTest {

    private final RedisSerializer<Object> serializer = CacheConfig.jsonSerializer();

    @Test
    @DisplayName("a VerifyResponse with timestamps and provenance survives the round trip")
    void verifyResponseRoundTrips() {
        ZonedDateTime at = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        List<ProvenanceRecord> provenance = new ArrayList<>();
        provenance.add(new ProvenanceRecord("gov_verified", "api_setu", "DigiLocker", at, 0.99));
        provenance.add(new ProvenanceRecord("corroborated", "skip", "n/a", at, 0.0));
        VerifyResponse original = new VerifyResponse(UUID.randomUUID(), UUID.randomUUID(),
            "income", "verified", 0.99, at.plusDays(365), provenance, null);

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(VerifyResponse.class);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("nested records keep their type instead of coming back as maps")
    void nestedRecordsKeepTheirType() {
        ZonedDateTime at = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        List<ProvenanceRecord> provenance = new ArrayList<>();
        provenance.add(new ProvenanceRecord("gov_verified", "api_setu", "DigiLocker", at, 0.99));
        VerifyResponse original = new VerifyResponse(UUID.randomUUID(), UUID.randomUUID(),
            "income", "verified", 0.99, at.plusDays(365), provenance, null);

        VerifyResponse restored =
            (VerifyResponse) serializer.deserialize(serializer.serialize(original));

        assertThat(restored.provenance()).hasSize(1);
        assertThat(restored.provenance().get(0)).isInstanceOf(ProvenanceRecord.class);
        assertThat(restored.provenance().get(0).attemptedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("a non-UTC zone keeps its instant and offset (the region id is not preserved)")
    void nonUtcZoneKeepsItsInstant() {
        ZonedDateTime kolkata = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
            .truncatedTo(ChronoUnit.MILLIS);
        VerifyResponse original = new VerifyResponse(UUID.randomUUID(), UUID.randomUUID(),
            "income", "verified", 0.5, kolkata, new ArrayList<>(), null);

        VerifyResponse restored =
            (VerifyResponse) serializer.deserialize(serializer.serialize(original));

        assertThat(restored.validUntil().toInstant()).isEqualTo(kolkata.toInstant());
        // ISO-8601 carries the offset, so the offset survives...
        assertThat(restored.validUntil().getOffset().getId())
            .isEqualTo(kolkata.getOffset().getId());
        // ...but not the region id: the contract promises an offset, not a zone.
        assertThat(restored.validUntil().getZone().getId()).isNotEqualTo("Asia/Kolkata");
    }
}
