package in.adivritti.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.adivritti.core.common.exception.GlobalExceptionHandler.ErrorBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The contract's auth and error-envelope claims must be true.
 *
 * <p>{@code Agent.md} makes {@code docs/openapi/core.yaml} the contract, and Task 7
 * added a bearer scheme plus 401/403 to all thirteen operations and declared
 * {@code at}/{@code path} on the error body. A hand-written document is only
 * trustworthy if something checks it, so this reads the YAML and also serialises the
 * real {@link ErrorBody} to prove the field names match.
 *
 * <p>Stated scope: this pins the security declaration and the envelope shape. It does
 * not attempt the full schema walk a generated client would do —
 * {@code WireFormatContractTest} covers the DTO property names.
 */
class SecurityContractTest {

    private static final Path CONTRACT = Path.of("..", "..", "docs", "openapi", "core.yaml");
    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    private static Map<String, Object> contract() throws IOException {
        assertThat(CONTRACT).as("%s (tests run from services/core)", CONTRACT.toAbsolutePath())
            .exists();
        return new Yaml().load(Files.readString(CONTRACT));
    }

    @Test
    @DisplayName("the document requires a bearer scheme and defines it")
    void bearerSchemeIsRequiredAndDefined() throws IOException {
        Map<String, Object> spec = contract();

        assertThat(asList(spec.get("security")))
            .as("every operation must require a credential by default")
            .containsExactly(Map.of("bearerAuth", List.of()));

        Map<String, Object> schemes =
            asMap(asMap(spec.get("components")).get("securitySchemes"));
        Map<String, Object> bearer = asMap(schemes.get("bearerAuth"));
        assertThat(bearer).as("components.securitySchemes.bearerAuth").isNotNull();
        assertThat(bearer).containsEntry("type", "http").containsEntry("scheme", "bearer");
    }

    @Test
    @DisplayName("every /v1 operation documents 401 and 403")
    void everyOperationDocumentsUnauthorisedAndForbidden() throws IOException {
        Map<String, Object> paths = asMap(contract().get("paths"));
        assertThat(paths).as("contract paths").isNotEmpty();

        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            for (Map.Entry<String, Object> op : asMap(path.getValue()).entrySet()) {
                if (!METHODS.contains(op.getKey())) continue;
                Map<String, Object> responses = asMap(asMap(op.getValue()).get("responses"));
                List<String> codes = responses == null ? List.of() : List.copyOf(responses.keySet());
                if (!codes.contains("401") || !codes.contains("403")) {
                    missing.put(op.getKey() + " " + path.getKey(), codes);
                }
            }
        }

        assertThat(missing)
            .as("operations missing a documented 401/403 — ScholarAccessGuard returns them "
                + "for a wrong usid claim or a missing role, and ConsentGate returns 403 "
                + "CONSENT_REQUIRED on a read without live consent")
            .isEmpty();
    }

    @Test
    @DisplayName("the shared Unauthorized and Forbidden responses exist and point at the envelope")
    void authResponsesAreDefined() throws IOException {
        Map<String, Object> responses =
            asMap(asMap(contract().get("components")).get("responses"));

        for (String name : List.of("Unauthorized", "Forbidden")) {
            Map<String, Object> response = asMap(responses.get(name));
            assertThat(response).as("components.responses.%s", name).isNotNull();
            assertThat(String.valueOf(response.get("description"))).isNotBlank();
            Map<String, Object> schema = asMap(asMap(
                asMap(asMap(response.get("content")).get("application/json")).get("schema")));
            assertThat(schema)
                .as("%s must return the same envelope as every other error", name)
                .containsEntry("$ref", "#/components/schemas/ErrorResponse");
        }
    }

    @Test
    @DisplayName("the envelope requires exactly the fields ErrorBody sends")
    void envelopeContractMatchesTheRecord() throws IOException {
        Map<String, Object> schemas =
            asMap(asMap(contract().get("components")).get("schemas"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) asMap(schemas.get("ErrorResponse")).get("required");

        assertThat(required)
            .as("GlobalExceptionHandler always populates all five")
            .containsExactlyInAnyOrder("error_code", "message", "details", "at", "path");

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        Map<String, Object> wire = asMap(mapper.convertValue(
            new ErrorBody("SCHOLAR_NOT_FOUND", "Scholar not found",
                Map.of("status", 404), ZonedDateTime.now(ZoneOffset.UTC), "/v1/claims"),
            Map.class));

        assertThat(wire.keySet())
            .as("the record must not drift from the declared envelope")
            .containsExactlyInAnyOrderElementsOf(required);
        assertThat(asMap(wire.get("details")))
            .as("details always carries the HTTP status")
            .containsEntry("status", 404);
    }

    @Test
    @DisplayName("the sort enum matches what the exception queue accepts")
    void sortEnumIsTheAcceptedSet() throws IOException {
        Map<String, Object> spec = contract();
        Map<String, Object> get = asMap(asMap(asMap(spec.get("paths")).get("/v1/admin/exceptions"))
            .get("get"));
        Object sort = null;
        for (Object candidate : asList(get.get("parameters"))) {
            Map<String, Object> parameter = asMap(candidate);
            if ("sort".equals(parameter.get("name"))) sort = parameter;
        }
        assertThat(sort).as("the exceptions operation documents its sort parameter").isNotNull();

        Map<String, Object> sortSchema = asMap(asMap(sort).get("schema"));
        assertThat(asList(sortSchema.get("enum"))).containsExactly(
            "breach_risk", "sla_deadline", "created_at");
        assertThat(sortSchema.get("default"))
            .as("the default must be a value the service actually honours")
            .isEqualTo("breach_risk");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}