package in.adivritti.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.dto.AdminDtos.ExceptionItem;
import in.adivritti.core.admin.dto.AdminDtos.ExceptionPage;
import in.adivritti.core.claims.dto.ClaimDtos.ClaimsListResponse;
import in.adivritti.core.consent.dto.ConsentDtos.AuditPage;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse.ProvenanceRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The wire format has to match {@code docs/openapi/core.yaml}.
 *
 * <p>{@code Agent.md} makes the OpenAPI document the contract and tells clients to be
 * generated from it, but the runtime emitted camelCase while the contract is
 * snake_case from top to bottom. Nothing detected it: the DTOs are records, so their
 * field names never appear in a test, and the generated client directory does not
 * exist yet. This test reads both artefacts and fails if they disagree.
 *
 * <p>Scope, stated plainly: it checks the property names the contract marks
 * {@code required} (and the contract's own naming convention), not the full schema
 * walk a generated client would do.
 */
class WireFormatContractTest {

    private static final Path APPLICATION_YML =
        Path.of("src", "main", "resources", "application.yml");
    private static final Path CONTRACT = Path.of("..", "..", "docs", "openapi", "core.yaml");

    @Test
    @DisplayName("the application config asks Jackson for the contract's snake_case convention")
    void jacksonIsConfiguredForSnakeCase() throws IOException {
        Map<String, Object> application = yaml(APPLICATION_YML);
        Map<String, Object> jackson = asMap(asMap(application.get("spring")).get("jackson"));

        assertThat(jackson)
            .as("spring.jackson in %s", APPLICATION_YML)
            .containsEntry("property-naming-strategy", "SNAKE_CASE");
    }

    @Test
    @DisplayName("outbound DTOs expose every field the contract marks required")
    void outboundDtosUseContractNames() throws IOException {
        Map<String, Object> schemas = asMap(asMap(yaml(CONTRACT).get("components")).get("schemas"));
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        for (Map.Entry<String, Object> sample : samples().entrySet()) {
            Map<String, Object> schema = asMap(schemas.get(sample.getKey()));
            assertThat(schema).as("contract schema %s", sample.getKey()).isNotNull();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertThat(required).as("required fields of %s", sample.getKey()).isNotNull();

            Map<String, Object> serialized =
                asMap(mapper.convertValue(sample.getValue(), Map.class));

            assertThat(serialized.keySet())
                .as("serialized %s must use the contract's property names (%s)",
                    sample.getValue().getClass().getSimpleName(), required)
                .containsAll(required);
        }
    }

    @Test
    @DisplayName("a contract-shaped verify body binds; a camelCase one does not")
    void inboundVerifyBodiesFollowTheContract() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // Spring Boot's default, so the negative case shows the bean-validation
            // failure a caller would see rather than an unknown-property exception.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        String usid = UUID.randomUUID().toString();
        VerifyRequest contractShaped = mapper.readValue(
            "{\"usid\":\"" + usid + "\",\"claim_type\":\"income\",\"evidence_ref\":\"doc-1\","
                + "\"idempotency_key\":\"idem-1\"}",
            VerifyRequest.class);

        assertThat(contractShaped.claimType()).isEqualTo("income");
        assertThat(contractShaped.evidenceRef()).isEqualTo("doc-1");
        assertThat(contractShaped.idempotencyKey()).isEqualTo("idem-1");

        VerifyRequest camelCase = mapper.readValue(
            "{\"usid\":\"" + usid + "\",\"claimType\":\"income\"}", VerifyRequest.class);

        assertThat(camelCase.claimType())
            .as("claim_type is the contract's name; claimType leaves the field null and "
                + "@NotBlank then rejects an otherwise legal request")
            .isNull();
    }

    private static Map<String, Object> samples() {
        ZonedDateTime at = ZonedDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("VerifyResponse", new VerifyResponse(UUID.randomUUID(), UUID.randomUUID(),
            "income", "verified", 0.99, at.plusDays(365),
            List.of(new ProvenanceRecord("gov_verified", "api_setu", "DigiLocker", at, 0.99)),
            null));
        samples.put("ExceptionItem", new ExceptionItem(UUID.randomUUID(), UUID.randomUUID(),
            null, "prematric", "submitted", 0.0, 0.5, at.plusDays(30)));
        samples.put("ExceptionPage", new ExceptionPage(List.of(), 0, 1, 20));
        samples.put("AuditPage", new AuditPage(List.of(), 0, 1, 20));
        samples.put("DashboardResponse",
            new DashboardResponse(UUID.randomUUID(), List.of(), null, List.of()));
        samples.put("ClaimsListResponse", new ClaimsListResponse(UUID.randomUUID(), List.of()));
        samples.put("CoverageGapResponse", new CoverageGapResponse(0, 0, 0, List.of()));
        return samples;
    }

    private static Map<String, Object> yaml(Path path) throws IOException {
        assertThat(path).as("%s (tests run from services/core)", path.toAbsolutePath()).exists();
        return new Yaml().load(Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
