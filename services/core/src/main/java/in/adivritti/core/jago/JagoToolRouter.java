package in.adivritti.core.jago;

import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.disbursement.repository.DisbursementRepository;
import in.adivritti.core.eligibility.EligibilityService;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JAGO+ Scholarship Skill: tool-calling surface, NOT a competing chatbot.
 * Each tool returns STRUCTURED output; callers template-fill the answer.
 * The LLM NEVER free-generates a status, amount, or eligibility verdict.
 *
 * <p>Tool output is read straight from the database so the chatbot can never
 * contradict the system of record. Money values are integer paise.
 */
@Service
public class JagoToolRouter {

    public static final Set<String> TOOLS = Set.of("get_my_applications", "check_eligibility",
        "explain_deficiency", "why_is_payment_pending", "next_action",
        "list_required_documents", "get_disbursement_history");

    private final ApplicationRepository applications;
    private final DisbursementRepository disbursements;
    private final DeficiencyRepository deficiencies;
    private final EligibilityService eligibility;

    public JagoToolRouter(ApplicationRepository applications,
        DisbursementRepository disbursements, DeficiencyRepository deficiencies,
        EligibilityService eligibility) {
        this.applications = applications;
        this.disbursements = disbursements;
        this.deficiencies = deficiencies;
        this.eligibility = eligibility;
    }

    public record ToolResult(String tool, UUID usid, Map<String, Object> output, String templateId) {}

    @Transactional(readOnly = true)
    public ToolResult invoke(String name, UUID usid, Map<String, Object> parameters) {
        if (name == null || !TOOLS.contains(name)) {
            throw new IllegalArgumentException("Unknown JAGO tool: " + name);
        }
        if (usid == null) throw new IllegalArgumentException("usid is required");
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        Map<String, Object> output = switch (name) {
            case "get_my_applications" -> Map.of("applications", applications(usid));
            case "check_eligibility" -> Map.of("verdicts", verdicts(usid, params));
            case "explain_deficiency" -> Map.of("deficiencies", openDeficiencies(usid));
            case "why_is_payment_pending" -> Map.of("disbursements", payments(usid));
            case "next_action" -> Map.of("action", nextAction(usid));
            case "list_required_documents" -> Map.of("documents", requiredDocuments(usid, params));
            case "get_disbursement_history" -> Map.of("history", payments(usid));
            default -> throw new IllegalStateException("Unhandled JAGO tool: " + name);
        };
        return new ToolResult(name, usid, output, "jago." + name + ".v1");
    }

    private List<Map<String, Object>> applications(UUID usid) {
        return applications.findByUsidOrderByCreatedAtDesc(usid).stream()
            .map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("application_id", a.id);
                m.put("scheme", a.scheme);
                m.put("academic_year", a.academicYear);
                m.put("stage", a.stage);
                m.put("current_actor", a.currentActor);
                m.put("sla_deadline", a.slaDeadline);
                return m;
            }).toList();
    }

    private List<Map<String, Object>> verdicts(UUID usid, Map<String, Object> params) {
        String year = params.get("academic_year") instanceof String s ? s : null;
        return eligibility.evaluate(new EligibilityRequest(usid, year)).verdicts().stream()
            .map(v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scheme", v.scheme());
                m.put("verdict", v.verdict());
                m.put("reasons", v.reasons());
                m.put("missing_claims", v.missingClaims());
                return m;
            }).toList();
    }

    private List<Map<String, Object>> openDeficiencies(UUID usid) {
        return deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(usid, "open").stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.id);
                m.put("type", d.type);
                m.put("message", d.message);
                m.put("resolution_route", d.resolutionRoute);
                m.put("created_at", d.createdAt);
                return m;
            }).toList();
    }

    private List<Map<String, Object>> payments(UUID usid) {
        return disbursements.findByUsidOrderByScheme(usid).stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scheme", d.scheme);
                m.put("status", d.status);
                m.put("sanctioned_amount_paise", d.sanctionedAmountPaise);
                m.put("paid_amount_paise", d.paidAmountPaise);
                m.put("pfms_ref", d.pfmsRef);
                m.put("failure_code", d.failureCode);
                m.put("failure_reason", d.failureReason);
                m.put("disbursed_at", d.disbursedAt);
                return m;
            }).toList();
    }

    private Map<String, Object> nextAction(UUID usid) {
        List<Map<String, Object>> open = openDeficiencies(usid);
        if (!open.isEmpty()) {
            Map<String, Object> first = new LinkedHashMap<>(open.get(0));
            first.put("type", "resolve_deficiency");
            return first;
        }
        return Map.of("type", "none",
            "message", "Nothing needs your attention right now.");
    }

    /**
     * Missing items come from the eligibility engine's own `missingClaims`, so the
     * document list can never drift from the rules that produced it.
     */
    private List<Map<String, Object>> requiredDocuments(UUID usid, Map<String, Object> params) {
        String scheme = params.get("scheme") instanceof String s ? s : null;
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Map<String, Object> verdict : verdicts(usid, params)) {
            if (scheme != null && !scheme.equalsIgnoreCase(String.valueOf(verdict.get("scheme")))) {
                continue;
            }
            Object missing = verdict.get("missing_claims");
            if (missing instanceof List<?> list) {
                for (Object claim : list) {
                    docs.add(Map.of("scheme", verdict.get("scheme"), "claim", String.valueOf(claim)));
                }
            }
        }
        return docs;
    }

    /** Guards against a caller asking for a USID that does not exist. */
    @Transactional(readOnly = true)
    public void requireScholar(UUID usid) {
        if (applications.findByUsidOrderByCreatedAtDesc(usid).isEmpty()
            && disbursements.findByUsidOrderByScheme(usid).isEmpty()
            && deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(usid, "open").isEmpty()) {
            throw new NotFoundException("SCHOLAR_NOT_FOUND", "No records for USID " + usid);
        }
    }
}
