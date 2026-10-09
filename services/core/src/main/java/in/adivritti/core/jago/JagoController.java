package in.adivritti.core.jago;

import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/jago/tool")
public class JagoController {

    private final JagoToolRouter router;
    private final ScholarAccessGuard access;

    public JagoController(JagoToolRouter router, ScholarAccessGuard access) {
        this.router = router;
        this.access = access;
    }

    public record ToolRequest(
        @NotNull(message = "usid is required") UUID usid,
        Map<String, Object> parameters) {}

    /**
     * Tool parameters are caller-controlled input to seven narrow tools (today:
     * {@code academic_year} and {@code scheme}, both scalars). A multi-megabyte
     * nested body used to be accepted and iterated, so bound the shape here.
     */
    static final int MAX_PARAMETERS = 32;
    static final int MAX_KEY_CHARS = 64;
    static final int MAX_VALUE_CHARS = 512;

    static Map<String, Object> validatedParameters(Map<String, Object> parameters) {
        if (parameters == null) return Map.of();
        if (parameters.size() > MAX_PARAMETERS) {
            throw new IllegalArgumentException(
                "at most " + MAX_PARAMETERS + " tool parameters are accepted");
        }
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() > MAX_KEY_CHARS) {
                throw new IllegalArgumentException("tool parameter keys are at most "
                    + MAX_KEY_CHARS + " characters");
            }
            Object value = entry.getValue();
            if (value instanceof String s && s.length() > MAX_VALUE_CHARS) {
                throw new IllegalArgumentException("tool parameter '" + key
                    + "' is too long (at most " + MAX_VALUE_CHARS + " characters)");
            }
            if (value instanceof Map || value instanceof List) {
                throw new IllegalArgumentException("tool parameter '" + key
                    + "' must be a scalar, not a nested object");
            }
        }
        return parameters;
    }

    @PostMapping("/{name}")
    ResponseEntity<JagoToolRouter.ToolResult> invoke(@PathVariable String name,
        @Valid @RequestBody ToolRequest req) {
        // The usid arrives in the body rather than the path, so nothing in the route
        // ties it to the caller. Without this the router will return another
        // scholar's disbursement history, PFMS references, amounts and failure
        // codes to anyone who supplies their USID.
        access.check(req.usid());
        return ResponseEntity.ok(router.invoke(name, req.usid(), validatedParameters(req.parameters())));
    }
}
