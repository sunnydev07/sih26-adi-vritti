package in.adivritti.core.claims;

import in.adivritti.core.claims.dto.ClaimDtos.ClaimDto;
import in.adivritti.core.claims.dto.ClaimDtos.ClaimsListResponse;
import in.adivritti.core.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClaimsService {

    /** Wallet read: verified claims reusable across all 5 schemes until expiry. */
    public ClaimsListResponse list(UUID usid, boolean includeExpired) {
        if (usid == null) throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found: null");
        List<ClaimDto> claims = List.of(); // repository-backed in full wiring
        if (!includeExpired) claims = claims.stream().filter(c -> !c.expired()).toList();
        return new ClaimsListResponse(usid, claims);
    }
}
