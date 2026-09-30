package in.adivritti.core.jago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.jago.JagoToolRouter.ToolResult;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JagoControllerTest {

    private static final String TOOL = "get_disbursement_history";
    private static final UUID USID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JagoToolRouter router = mock(JagoToolRouter.class);
    private final JagoController controller =
        new JagoController(router, new ScholarAccessGuard(false));

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void runAs(Authentication auth, ThrowingRunnable body) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        try {
            body.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Authentication jwt(UUID usid, String... roles) {
        var builder = Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(300));
        if (usid != null) builder.claim(ScholarAccessGuard.USID_CLAIM, usid.toString());
        if (roles.length > 0) builder.claim("roles", List.of(roles));
        var authorities = java.util.Arrays.stream(roles)
            .map(r -> (org.springframework.security.core.GrantedAuthority)
                new SimpleGrantedAuthority("ROLE_" + r))
            .toList();
        return new UsernamePasswordAuthenticationToken(builder.build(), null, authorities);
    }

    private static String body(UUID usid) {
        return usid == null ? "{}" : "{\"usid\":\"" + usid + "\"}";
    }

    @Test
    void ownerMayInvokeAToolForTheirOwnUsid() throws Exception {
        var expected = new ToolResult(TOOL, USID, Map.of("history", List.of()), "jago." + TOOL + ".v1");
        when(router.invoke(TOOL, USID, Map.of())).thenReturn(expected);
        runAs(jwt(USID), () ->
            assertEquals(expected, controller.invoke(TOOL, new JagoController.ToolRequest(USID, null))
                .getBody()));
    }

    @Test
    void anotherScholarsUsidIsRefusedAndTheRouterIsNeverReached() throws Exception {
        // The IDOR: the usid is a body field, so nothing in the route tied it to the
        // caller. Reading someone else's disbursement history, PFMS refs and amounts
        // must be impossible.
        runAs(jwt(USID), () -> {
            ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> controller.invoke(TOOL, new JagoController.ToolRequest(OTHER, null)));
            assertEquals("SCHOLAR_ACCESS_DENIED", e.getErrorCode());
            verify(router, never()).invoke(anyString(), any(), any());
        });
    }

    @Test
    void officerMayInvokeAToolForAnyUsid() throws Exception {
        var expected = new ToolResult(TOOL, OTHER, Map.of("history", List.of()), "jago." + TOOL + ".v1");
        when(router.invoke(TOOL, OTHER, Map.of())).thenReturn(expected);
        runAs(jwt(null, "OFFICER"), () ->
            assertEquals(expected, controller.invoke(TOOL, new JagoController.ToolRequest(OTHER, null))
                .getBody()));
    }

    @Test
    void anonymousCallIsRefused() {
        assertThrows(ForbiddenException.class,
            () -> controller.invoke(TOOL, new JagoController.ToolRequest(USID, null)));
        verify(router, never()).invoke(anyString(), any(), any());
    }

    @Test
    void bodyWithoutUsidIsRejectedByBeanValidation() throws Exception {
        // @NotNull on the record only fires if the @RequestBody is @Valid.
        mvc.perform(post("/v1/jago/tool/" + TOOL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(null)))
            .andExpect(status().isBadRequest());
        verify(router, never()).invoke(anyString(), any(), any());
    }

    @Test
    void foreignUsidOverHttpIsRefusedBeforeTheToolRuns() throws Exception {
        runAs(jwt(USID), () -> {
            assertThrows(Exception.class, () -> mvc.perform(post("/v1/jago/tool/" + TOOL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(OTHER))).andReturn());
            verify(router, never()).invoke(anyString(), any(), any());
        });
    }

    @Test
    void ownUsidOverHttpReachesTheTool() throws Exception {
        var expected = new ToolResult(TOOL, USID, Map.of("history", List.of()), "jago." + TOOL + ".v1");
        when(router.invoke(eq(TOOL), eq(USID), any())).thenReturn(expected);
        runAs(jwt(USID), () -> {
            var response = mvc.perform(post("/v1/jago/tool/" + TOOL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(USID))).andReturn();
            assertEquals(200, response.getResponse().getStatus());
            assertTrue(response.getResponse().getContentAsString().contains(TOOL));
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
