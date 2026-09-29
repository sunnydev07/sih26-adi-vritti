package in.adivritti.core.disbursement.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** DBT disbursement rows with decoded PFMS failure guidance. */
public final class DisbursementDtos {
    private DisbursementDtos() {}

    public record DisbursementDto(UUID id, String scheme, long sanctionedAmountPaise, long paidAmountPaise,
        String pfmsRef, String status, String failureCode, String decodedFailureReason,
        String fixInstructions, ZonedDateTime disbursedAt) {}

    public record DisbursementListResponse(UUID usid, List<DisbursementDto> disbursements) {}
}
