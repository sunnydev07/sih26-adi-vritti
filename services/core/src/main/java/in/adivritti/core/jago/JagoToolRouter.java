package in.adivritti.core.jago;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * JAGO+ Scholarship Skill: tool-calling surface, NOT a competing chatbot.
 * Each tool returns STRUCTURED output; callers template-fill the answer.
 * The LLM NEVER free-generates a status, amount, or eligibility verdict.
 */
@Service
public class JagoToolRouter {

    public static final Set<String> TOOLS = Set.of("get_my_applications", "check_eligibility",
        "explain_deficiency", "why_is_payment_pending", "next_action",
        "list_required_documents", "get_disbursement_history");

    public record ToolResult(String tool, UUID usid, Map<String, Object> output, String templateId) {}

    public ToolResult invoke(String name, UUID usid, Map<String, Object> parameters) {
        if (!TOOLS.contains(name)) throw new IllegalArgumentException("Unknown JAGO tool: " + name);
        Map<String, Object> output = switch (name) {
            case "get_my_applications" -> Map.of("applications", java.util.List.of());
            case "check_eligibility" -> Map.of("verdicts", java.util.List.of());
            case "explain_deficiency" -> Map.of("deficiency", Map.of());
            case "why_is_payment_pending" -> Map.of("disbursements", java.util.List.of());
            case "next_action" -> Map.of("action", Map.of());
            case "list_required_documents" -> Map.of("documents", java.util.List.of());
            case "get_disbursement_history" -> Map.of("history", java.util.List.of());
            default -> Map.of();
        };
        return new ToolResult(name, usid, output, "jago." + name + ".v1");
    }
}
