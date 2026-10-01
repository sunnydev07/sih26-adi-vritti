package in.adivritti.core.claims;

import in.adivritti.core.claims.dto.ClaimDtos.ClaimDto;
import in.adivritti.core.claims.dto.ClaimDtos.ClaimsListResponse;
import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.ConsentGate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimsService {

    private final ClaimRepository claims;
    private final ConsentGate consent;

    public ClaimsService(ClaimRepository claims, ConsentGate consent) {
        this.claims = claims;
        this.consent = consent;
    }

    /** Wallet read: verified claims reusable across all 5 schemes until expiry. */
    @Transactional(readOnly = true)
    public ClaimsListResponse list(UUID usid, boolean includeExpired) {
        if (usid == null) throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found");
        consent.requireConsent(usid, "claim_verification", "claims");
        List<Claim> rows = includeExpired
            ? claims.findByUsidOrderByVerifiedAtDesc(usid)
            : claims.findLiveByUsid(usid);
        List<ClaimDto> dtos = rows.stream().map(ClaimsService::toDto).toList();
        return new ClaimsListResponse(usid, dtos);
    }

    static ClaimDto toDto(Claim c) {
        return new ClaimDto(c.id, c.usid, c.claimType, c.source, c.method, c.confidence,
            c.verifiedAt, c.validUntil, c.expired());
    }
}
