package in.adivritti.core.eligibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse.SchemeVerdict;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final SchemeRuleVersionRepository mirror;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path rulesRoot;

    /**
     * Rules-as-data is read once per (year, scheme) and cached in-process.
     *
     * <p>An {@link java.util.concurrent.atomic.AtomicReference} to an immutable map,
     * not a {@link java.util.concurrent.ConcurrentHashMap}. The cache is cleared
     * wholesale on a timer ({@link #invalidateRuleCache()}) while evaluations read
     * it concurrently, and a plain concurrent map makes that a lost update: a load
     * that started before the clear and finished after it re-inserts into a map
     * the clear already emptied, so the edit the invalidation was meant to pick up
     * is masked for another full interval. Reads also came in pairs —
     * {@code containsKey} then {@code get} — which is a second race: a clear
     * between them turns a cache hit into a null return, i.e. a fail-closed
     * "no rules" verdict for a scheme that has rules.
     *
     * <p>With a snapshot reference, a read sees either the whole old map or the
     * whole new one and never a half-cleared state, and invalidation is a single
     * atomic swap.
     */
    private final java.util.concurrent.atomic.AtomicReference<
        Map<String, List<Map<String, Object>>>> ruleCache =
        new java.util.concurrent.atomic.AtomicReference<>(Map.of());

    private List<Map<String, Object>> cachedRules(String cacheKey) {
        return ruleCache.get().get(cacheKey);
    }

    /** Copy-on-write insert: a reader mid-evaluation keeps its snapshot. */
    private void cacheRules(String cacheKey, List<Map<String, Object>> rules) {
        ruleCache.updateAndGet(current -> {
            Map<String, List<Map<String, Object>>> next = new java.util.LinkedHashMap<>(current);
            next.put(cacheKey, rules);
            return Map.copyOf(next);
        });
    }

    public EligibilityService(RuleEngine engine, ClaimRepository claims,
        ClaimValueCipher cipher, SchemeRuleVersionRepository mirror,
        @Value("${app.rules-path:../../packages/rules}") String rulesPath) {
        this.engine = engine;
        this.claims = claims;
        this.cipher = cipher;
        this.mirror = mirror;
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
        // The verdict label keeps the scheme key as requested (lowercase, matching
        // the rules filenames and application.scheme). It was previously uppercased,
        // so DashboardService keyed its verdict map by "PRE-MATRIC" while looking it
        // up with "pre-matric" — every scheme rendered eligibility "unknown".
        String label = scheme;
        try {
            List<Map<String, Object>> rules = loadRules(year, scheme);
            if (rules == null || rules.isEmpty()) {
                // Fail closed: previously only a missing rule set was rejected, so an
                // empty-but-present rule list evaluated zero outcomes and returned
                // "eligible" for every student. An empty rule set is a system fault,
                // never a clean record — report it as missing, not eligible.
                if (rules != null) {
                    log.error("Empty rule set for scheme={} year={}; failing closed",
                        scheme, year);
                }
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

    /**
     * Load and cache rules for one academic year. Disk is primary; the
     * startup-validated {@code scheme_rule_version} mirror is the fallback when
     * a file is missing or unreadable. Returns null when neither has the rules.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadRules(String year, String scheme) {
        String cacheKey = year + "/" + scheme;
        List<Map<String, Object>> hit = cachedRules(cacheKey);
        if (hit != null) return hit;

        Path file = rulesRoot.resolve(year).resolve(scheme + ".json").normalize();
        // Defence in depth: the resolved path must stay under the rules root.
        if (file.startsWith(rulesRoot) && Files.isRegularFile(file)) {
            try {
                Map<String, Object> doc = mapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8), new TypeReference<>() {});
                List<Map<String, Object>> parsed = parseRulesDoc(doc);
                if (parsed != null) {
                    cacheRules(cacheKey, parsed);
                    return parsed;
                }
            } catch (Exception e) {
                log.error("Could not read rules file {}: {}", file.getFileName(), e.getMessage());
            }
        }
        List<Map<String, Object>> mirrored = loadMirrorRules(year, scheme);
        if (mirrored != null) {
            cacheRules(cacheKey, mirrored);
        }
        return mirrored;
    }

    /**
     * Drop cached rule files so an edit to {@code packages/rules} is picked up without
     * a restart.
     *
     * <p>The container mount is read-only, so there is no inotify signal available and a
     * periodic clear is the only invalidation mechanism. The tick runs every 10 minutes
     * by default and no-ops unless something is cached, so the default costs nothing;
     * {@code infra/docker-compose.override.yml} lowers it to 2s so a rules edit shows up
     * in a running dev container. Production keeps the default, because a rule file is a
     * deploy-time artefact there, not something edited under a running pod.
     *
     * <p>The mirror fallback is NOT refreshed here — {@code scheme_rule_version} is
     * written once at startup by {@code RuleBootstrapRunner}, so a disk edit is picked
     * up but its mirror copy stays stale until the next boot. That is intentional:
     * re-validating and re-mirroring on every tick would let a malformed edit land in
     * the database, which is what startup validation exists to prevent.
     */
    @Scheduled(fixedDelayString = "${app.rules-watch-interval-ms:600000}")
    public void invalidateRuleCache() {
        Map<String, List<Map<String, Object>>> dropped = ruleCache.getAndSet(Map.of());
        if (dropped.isEmpty()) return;
        log.info("Rules cache invalidated ({} entries); the next evaluation re-reads from "
            + "packages/rules.", dropped.size());
    }

    /** Shared shape check for disk documents and mirror rows. */
    private List<Map<String, Object>> parseRulesDoc(Map<String, Object> doc) {
        if (doc == null) return null;
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
        return parsed;
    }

    /**
     * Fallback reader for the {@code scheme_rule_version} mirror: the validated
     * copy {@code RuleBootstrapRunner} wrote at startup. This is the table's
     * request-time reader — disk stays primary, so a mirror row can never shadow
     * a newer file.
     */
    private List<Map<String, Object>> loadMirrorRules(String year, String scheme) {
        try {
            return mirror.findBySchemeIgnoreCaseAndAcademicYear(scheme, year)
                .map(row -> {
                    try {
                        Map<String, Object> doc = mapper.readValue(
                            row.rulesJson, new TypeReference<>() {});
                        return parseRulesDoc(doc);
                    } catch (Exception e) {
                        log.error("Could not parse mirrored rules for scheme={} year={}",
                            scheme, year, e);
                        return null;
                    }
                })
                .orElse(null);
        } catch (RuntimeException e) {
            // The mirror must never take eligibility down: no DB on this path
            // (unit tests, degraded deploys) behaves like "no mirror row".
            log.debug("Mirror lookup failed for scheme={} year={}: {}", scheme, year,
                e.getMessage());
            return null;
        }
    }
}
