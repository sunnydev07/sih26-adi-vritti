package in.adivritti.core.jago;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/jago/tool")
public class JagoController {

    private final JagoToolRouter router;

    public JagoController(JagoToolRouter router) {
        this.router = router;
    }

    public record ToolRequest(UUID usid, Map<String, Object> parameters) {}

    @PostMapping("/{name}")
    ResponseEntity<JagoToolRouter.ToolResult> invoke(@PathVariable String name,
        @RequestBody ToolRequest req) {
        return ResponseEntity.ok(router.invoke(name, req.usid(),
            req.parameters() != null ? req.parameters() : Map.of()));
    }
}
