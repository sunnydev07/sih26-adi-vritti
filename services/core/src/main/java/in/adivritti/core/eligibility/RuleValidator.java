package in.adivritti.core.eligibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADR-001 startup validation: every {@code <year>/*.json} rule file under the
 * rules root must satisfy {@code packages/rules/schema.json}.
 *
 * <p>Scope is deliberate: year directories hold scheme rule files ONLY.
 * Cross-scheme metadata (e.g. {@code compatibility-matrix.json}, a different
 * document kind no service reads) lives at the rules root, where this
 * validator does not look. A non-conforming file inside a year directory is a
 * startup failure, never a skip.
 *
 * <p>The schema document itself is the authority — it is parsed at construction
 * and every rule file is checked against it with the reference validator, so a
 * rule edit that drifts from the schema fails the service at startup instead of
 * silently changing eligibility verdicts at request time. Missing schema or
 * rules root is a startup failure, not a warning: running with unvalidated
 * rules-as-data is exactly the failure mode ADR-001 exists to prevent.
 */
@Component
public class RuleValidator {

    private static final Logger log = LoggerFactory.getLogger(RuleValidator.class);

    /** Year directories look like {@code 2026-27}; anything else is not a rule year. */
    private static final Pattern YEAR_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path rulesRoot;
    private final JsonSchema schema;

    public RuleValidator(
        @Value("${app.rules-path:../../packages/rules}") String rulesPath) {
        this.rulesRoot = Path.of(rulesPath).toAbsolutePath().normalize();
        this.schema = loadSchema(this.rulesRoot.resolve("schema.json"));
    }

    private JsonSchema loadSchema(Path schemaFile) {
        if (!Files.isRegularFile(schemaFile)) {
            throw new IllegalStateException(
                "Rule schema not found at " + schemaFile
                    + ": cannot validate rules-as-data at startup.");
        }
        try {
            JsonNode schemaNode = mapper.readTree(
                Files.readString(schemaFile, StandardCharsets.UTF_8));
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(schemaNode);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read rule schema " + schemaFile, e);
        }
    }

    /**
     * Validates every {@code <year>/*.json} file and returns the parsed documents
     * keyed by {@code year/file-name}. Throws {@link IllegalStateException}
     * aggregating every violation when any file is unreadable or invalid.
     */
    public Map<String, JsonNode> validateAll() {
        if (!Files.isDirectory(rulesRoot)) {
            throw new IllegalStateException(
                "Rules root is not a directory: " + rulesRoot);
        }
        Map<String, JsonNode> docs = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> years = Files.list(rulesRoot)) {
            for (Path year : years.filter(Files::isDirectory)
                .filter(p -> YEAR_PATTERN.matcher(p.getFileName().toString()).matches())
                .sorted().toList()) {
                validateYear(year, docs, violations);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list rules root " + rulesRoot, e);
        }
        if (docs.isEmpty()) {
            violations.add("no <year>/*.json rule files found under " + rulesRoot);
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Rule validation failed:\n- " + String.join("\n- ", violations));
        }
        log.info("Validated {} rule files under {}", docs.size(), rulesRoot);
        return docs;
    }

    private void validateYear(Path year, Map<String, JsonNode> docs, List<String> violations) {
        try (Stream<Path> files = Files.list(year)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted().toList()) {
                String key = year.getFileName() + "/"
                    + file.getFileName().toString().replaceAll("\\.json$", "");
                JsonNode doc;
                try {
                    doc = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    violations.add(key + ": unreadable (" + e.getMessage() + ")");
                    continue;
                }
                Set<ValidationMessage> errors = schema.validate(doc);
                if (!errors.isEmpty()) {
                    List<String> messages = errors.stream()
                        .map(ValidationMessage::getMessage).sorted().toList();
                    violations.add(key + ": " + String.join("; ", messages));
                    continue;
                }
                docs.put(key, doc);
            }
        } catch (IOException e) {
            violations.add(year.getFileName() + ": cannot list directory (" + e.getMessage() + ")");
        }
    }
}
