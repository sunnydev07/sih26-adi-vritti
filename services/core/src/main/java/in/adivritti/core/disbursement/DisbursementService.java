package in.adivritti.core.disbursement;

import in.adivritti.core.disbursement.dto.DisbursementDtos.DisbursementListResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DisbursementService {

    private final FailureDecoder decoder;

    public DisbursementService(FailureDecoder decoder) {
        this.decoder = decoder;
    }

    public DisbursementListResponse list(UUID usid) {
        // Repository-backed in full wiring. Decoding shown on whatever rows exist.
        return new DisbursementListResponse(usid, List.of());
    }

    FailureDecoder.Decoded decode(String code) {
        return decoder.decode(code);
    }
}
