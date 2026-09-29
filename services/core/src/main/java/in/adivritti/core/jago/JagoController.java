package in.adivritti.core.jago;

import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    @PostMapping("/{name}")
    ResponseEntity<JagoToolRouter.ToolResult> invoke(@PathVariable String name,
        @Valid @RequestBody ToolRequest req) {
        // The usid arrives in the body rather than the path, so nothing in the route
        // ties it to the caller. Without this the router will return another
        // scholar's disbursement history, PFMS references, amounts and failure
        // codes to anyone who supplies their USID.
        access.check(req.usid());
        return ResponseEntity.ok(router.invoke(name, req.usid(),
            req.parameters() != null ? req.parameters() : Map.of()));
    }
}
