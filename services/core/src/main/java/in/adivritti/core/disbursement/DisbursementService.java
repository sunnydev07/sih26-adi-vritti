package in.adivritti.core.disbursement;

import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.ConsentGate;
import in.adivritti.core.disbursement.dto.DisbursementDtos.DisbursementDto;
import in.adivritti.core.disbursement.dto.DisbursementDtos.DisbursementListResponse;
import in.adivritti.core.disbursement.entity.Disbursement;
import in.adivritti.core.disbursement.repository.DisbursementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisbursementService {

    private final FailureDecoder decoder;
    private final DisbursementRepository disbursements;
    private final ConsentGate consent;

    public DisbursementService(FailureDecoder decoder, DisbursementRepository disbursements,
        ConsentGate consent) {
        this.decoder = decoder;
        this.disbursements = disbursements;
        this.consent = consent;
    }

    /** Every failed row is decoded into a plain-language cause + concrete fix. */
    @Transactional(readOnly = true)
    public DisbursementListResponse list(UUID usid) {
        if (usid == null) {
            throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found: null");
        }
        consent.requireConsent(usid, "disbursement_tracking", "disbursements");
        List<DisbursementDto> items = disbursements.findByUsidOrderByScheme(usid).stream()
            .map(this::toDto).toList();
        return new DisbursementListResponse(usid, items);
    }

    private DisbursementDto toDto(Disbursement d) {
        FailureDecoder.Decoded decoded = decoder.decode(d.failureCode);
        return new DisbursementDto(d.id, d.scheme, d.sanctionedAmountPaise, d.paidAmountPaise,
            d.pfmsRef, d.status, d.failureCode, decoded.cause(), decoded.fix(),
            d.disbursedAt);
    }

    FailureDecoder.Decoded decode(String code) {
        return decoder.decode(code);
    }
}
