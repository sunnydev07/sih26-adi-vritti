package in.adivritti.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.adivritti.core.common.exception.ForbiddenException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class ScholarAccessGuardTest {

    private static final UUID USID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ScholarAccessGuard guard = new ScholarAccessGuard();

    private static void runAs(Authentication auth, Runnable body) {
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
        var jwt = builder.build();
        var authorities = java.util.Arrays.stream(roles)
            .map(r -> (org.springframework.security.core.GrantedAuthority)
                new SimpleGrantedAuthority("ROLE_" + r))
            .toList();
        return new UsernamePasswordAuthenticationToken(jwt, null, authorities);
    }

    @Test
    void ownerMayReadTheirOwnRecord() {
        runAs(jwt(USID), () -> guard.check(USID));
    }

    @Test
    void anotherStudentsRecordIsRefused() {
        // The IDOR: any caller could pass any USID in the path.
        runAs(jwt(USID), () -> {
            ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> guard.check(OTHER));
            assertEquals("SCHOLAR_ACCESS_DENIED", e.getErrorCode());
        });
    }

    @Test
    void officerMayReadAnyScholar() {
        runAs(jwt(null, "OFFICER"), () -> guard.check(OTHER));
    }

    @Test
    void adminMayReadAnyScholar() {
        runAs(jwt(null, "ADMIN"), () -> guard.check(OTHER));
    }

    @Test
    void anonymousIsRefused() {
        SecurityContextHolder.clearContext();
        ForbiddenException e = assertThrows(ForbiddenException.class, () -> guard.check(USID));
        assertEquals("AUTHENTICATION_REQUIRED", e.getErrorCode());
    }

    @Test
    void tokenWithoutUsidClaimCannotSelfAccess() {
        runAs(jwt(null), () ->
            assertThrows(ForbiddenException.class, () -> guard.check(USID)));
    }

    @Test
    void malformedUsidClaimIsIgnored() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none")
                .claim(ScholarAccessGuard.USID_CLAIM, "not-a-uuid")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build(),
            null, List.of());
        runAs(auth, () -> assertThrows(ForbiddenException.class, () -> guard.check(USID)));
    }

    @Test
    void accessorNameIsTheAuthenticatedPrincipal() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "officer-42", null, List.of(new SimpleGrantedAuthority("ROLE_OFFICER")));
        runAs(auth, () -> assertEquals("officer-42", guard.accessorName()));
    }

    @Test
    void roleDetectionIsExact() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "x", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        assertFalse(ScholarAccessGuard.hasRole(auth, ScholarAccessGuard.ROLE_OFFICER));
        assertNull(ScholarAccessGuard.callerUsid(auth));
    }
}
