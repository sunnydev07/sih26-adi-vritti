package in.adivritti.core.security;

import in.adivritti.core.common.exception.ForbiddenException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The single authorization policy for scholar-scoped and officer-scoped routes.
 *
 * <p>{@code GET /v1/scholars/{usid}/claims} and friends took a USID straight from
 * the path and returned the data for whoever asked. USIDs are UUIDs, which are not
 * a secret, so this was a textbook IDOR over income, bank and Aadhaar-derived data.
 *
 * <p>A caller may read their own record, or any record if they hold an officer or
 * admin role. {@link #checkOfficer()} is the stricter gate for cross-scholar
 * aggregates, the exception queue and identity resolution.
 *
 * <p>{@code app.security.allow-insecure-dev} is honoured here, not only in the filter
 * chain. The flag's contract is "every /v1 endpoint is UNAUTHENTICATED for local
 * demos", and docs/specs/demo-path.md is a token-free curl walk-through with no
 * dev-token helper anywhere — so a layer that still demanded a credential would be
 * broken in the one mode it exists to serve. The prod profile sets the flag false, and
 * every bypass is logged.
 */
@Component
public class ScholarAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ScholarAccessGuard.class);

    /** Claim carrying the caller's own USID. */
    public static final String USID_CLAIM = "usid";
    public static final String ROLE_OFFICER = "ROLE_OFFICER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final boolean allowInsecureDev;
    private final AtomicBoolean devBypassLogged = new AtomicBoolean();

    public ScholarAccessGuard(
        @Value("${app.security.allow-insecure-dev:false}") boolean allowInsecureDev) {
        this.allowInsecureDev = allowInsecureDev;
    }

    public void check(UUID usid) {
        if (insecureDev()) {
            return;
        }
        Authentication auth = current();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Authentication required");
        }
        if (hasRole(auth, ROLE_OFFICER) || hasRole(auth, ROLE_ADMIN)) {
            return;
        }
        UUID callerUsid = callerUsid(auth);
        if (callerUsid == null || !callerUsid.equals(usid)) {
            // Deliberately the same message whether the record exists or not, so the
            // endpoint cannot be used to probe which USIDs are real.
            throw new ForbiddenException("SCHOLAR_ACCESS_DENIED",
                "You are not permitted to access this scholar's record");
        }
    }

    /**
     * Officer/admin only. Identity resolution mints and links USIDs, and the exception
     * queue exposes student names; a scholar token must reach neither. The failure this
     * closes: any authenticated principal could call the resolver with arbitrary
     * Aadhaar/demographic records and use the answer to probe who is already enrolled.
     */
    public void checkOfficer() {
        if (insecureDev()) {
            return;
        }
        Authentication auth = current();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Authentication required");
        }
        if (!hasRole(auth, ROLE_OFFICER) && !hasRole(auth, ROLE_ADMIN)) {
            throw new ForbiddenException("OFFICER_ROLE_REQUIRED",
                "This operation requires an officer or admin role");
        }
    }

    private boolean insecureDev() {
        if (allowInsecureDev && devBypassLogged.compareAndSet(false, true)) {
            log.warn("SECURITY: app.security.allow-insecure-dev=true — ownership and role "
                + "checks are DISABLED for every route. Local demos only.");
        }
        return allowInsecureDev;
    }

    /** The authenticated officer/admin identity, for the access audit trail. */
    public String accessorName() {
        Authentication auth = current();
        return auth == null ? "anonymous" : auth.getName();
    }

    private static Authentication current() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    static boolean hasRole(Authentication auth, String role) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        return authorities != null && authorities.stream()
            .anyMatch(a -> role.equals(a.getAuthority()));
    }

    /** Reads the caller's USID from the JWT {@code usid} claim. */
    static UUID callerUsid(Authentication auth) {
        if (!(auth.getPrincipal() instanceof Jwt jwt)) return null;
        Object claim = jwt.getClaims().get(USID_CLAIM);
        if (claim == null) return null;
        try {
            return UUID.fromString(String.valueOf(claim));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<String> describe(Authentication auth) {
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
