package in.adivritti.core.eligibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse.SchemeVerdict;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EligibilityService {

    private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

    private static final List<String> SCHEMES =
        List.of("pre-matric", "post-matric", "top-class", "nfst", "nos");
    private static final String DEFAULT_YEAR = "2026-27";

    /** Blocks path traversal via the academicYear request parameter. */
    private static final Pattern YEAR_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

    private final RuleEngine engine;
    private final ClaimRepository claims;
    private final ClaimValueCipher cipher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path rulesRoot;

    /** Rules-as-data is read once per (year, scheme) and cached in-process. */
    private final Map<String, List<Map<String, Object>>> ruleCache = new ConcurrentHashMap<>();

    public EligibilityService(RuleEngine engine, ClaimRepository claims,
        ClaimValueCipher cipher, @Value("${app.rules-path:../../packages/rules}") String rulesPath) {
        this.engine = engine;
        this.claims = claims;
        this.cipher = cipher;
        this.rulesRoot = Path.of(rulesPath).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public EligibilityResponse evaluate(EligibilityRequest req) {
        if (req == null || req.usid() == null) {
            throw new IllegalArgumentException("usid is required");
        }
        String year = normalizeYear(req.academicYear());
        Map<String, Object> claims = claimSnapshot(req.usid());

        List<SchemeVerdict> verdicts = new ArrayList<>(SCHEMES.size());
        for (String scheme : SCHEMES) {
            verdicts.add(evaluateScheme(scheme, year, claims));
        }
        return new EligibilityResponse(req.usid(), year, verdicts);
    }

    /**
     * Reject anything that is not a plain academic year before it reaches the
     * filesystem. Package-private so the traversal guard is directly testable.
     */
    static String normalizeYear(String academicYear) {
        if (academicYear == null || academicYear.isBlank()) return DEFAULT_YEAR;
        String trimmed = academicYear.trim();
        if (!YEAR_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                "academicYear must look like 2026-27 (received: " + trimmed + ")");
        }
        return trimmed;
    }

    /**
     * Live claims keyed by claim type, for the rules engine.
     *
     * <p>Encrypted values are decrypted for evaluation only — never written back or
     * logged. A claim that was verified without a value (see
     * {@code VerificationOrchestrator.VALUELESS}) is skipped, so a valueless wallet
     * entry can never satisfy an eligibility rule with an invented number.
     */
    private Map<String, Object> claimSnapshot(java.util.UUID usid) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        for (Object[] row : claims.findLiveByUsidWithTypes(usid)) {
            String type = (String) row[0];
            var claim = (in.adivritti.core.claims.entity.Claim) row[1];
            if (type == null) continue;
            String value;
            try {
                value = cipher.open(claim.valueEncrypted);
            } catch (RuntimeException e) {
                // A claim that will not decrypt is treated as absent rather than
                // silently satisfying a rule.
                log.warn("Skipping unreadable claim type={} usid={}", type, usid);
                continue;
            }
            if (value == null || value.isBlank()) {
                log.debug("Skipping valueless claim type={} usid={}", type, usid);
                continue;
            }
            snapshot.put(type, coerce(value));
        }
        return snapshot;
    }

    /**
     * Rules are authored with typed thresholds (numbers, arrays, strings), but a
     * claim value arrives from the database as text. A numeric string is converted
     * so {@code lte}/{@code gte}/{@code in} compare numerically rather than
     * throwing on a type mismatch.
     */
    static Object coerce(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("^-?\\d{1,18}$")) {
            try {
                long asLong = Long.parseLong(trimmed);
                return (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE)
                    ? (Object) (int) asLong
                    : (Object) asLong;
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        if (trimmed.equals("true")) return Boolean.TRUE;
        if (trimmed.equals("false")) return Boolean.FALSE;
        return trimmed;
    }

    private SchemeVerdict evaluateScheme(String scheme, String year, Map<String, Object> claims) {
        String label = scheme.toUpperCase(Locale.ROOT);
        try {
            List<Map<String, Object>> rules = loadRules(year, scheme);
            if (rules == null) {
                return new SchemeVerdict(label, "missing_items",
                    List.of("Rules are not published for " + year), List.of());
            }
            var outcomes = engine.evaluate(rules, claims);
            List<String> reasons = outcomes.stream()
                .filter(o -> !o.passed()).map(RuleEngine.RuleOutcome::reason).toList();
            List<String> missing = outcomes.stream()
                .filter(o -> !o.passed()).map(RuleEngine.RuleOutcome::missingClaim).toList();

            if (reasons.isEmpty()) {
                return new SchemeVerdict(label, "eligible", List.of(), List.of());
            }
            // A rule that failed because the claim is simply absent is a missing
            // item the student can supply; anything else is a hard ineligibility.
            boolean anyMissing = missing.stream().anyMatch(m -> !claims.containsKey(m));
            return new SchemeVerdict(label,
                anyMissing ? "missing_items" : "not_eligible", reasons, missing);
        } catch (RuntimeException e) {
            // Never surface a filesystem path or parser message to the client.
            log.error("Eligibility evaluation failed for scheme={} year={}", scheme, year, e);
            return new SchemeVerdict(label, "missing_items",
                List.of("Eligibility rules for this scheme are currently unavailable."),
                List.of());
        }
    }

    /** Load and cache rules for one academic year. Returns null when absent. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadRules(String year, String scheme) {
        String cacheKey = year + "/" + scheme;
        if (ruleCache.containsKey(cacheKey)) return ruleCache.get(cacheKey);

        Path file = rulesRoot.resolve(year).resolve(scheme + ".json").normalize();
        // Defence in depth: the resolved path must stay under the rules root.
        if (!file.startsWith(rulesRoot) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            Map<String, Object> doc = mapper.readValue(
                Files.readString(file, StandardCharsets.UTF_8), new TypeReference<>() {});
            Object rules = doc.get("rules");
            if (!(rules instanceof List<?> list)) return null;
            List<Map<String, Object>> parsed = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    m.forEach((k, v) -> entry.put(String.valueOf(k), v));
                    parsed.add(entry);
                }
            }
            ruleCache.put(cacheKey, parsed);
            return parsed;
        } catch (Exception e) {
            log.error("Could not read rules file {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }
}
