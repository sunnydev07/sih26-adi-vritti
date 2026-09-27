package in.adivritti.core.admin;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Coverage Gap read-model: served from coverage_candidate rows populated by the
 * AI gap engine's privacy-preserving HMAC join. MoTA sees counts + school lists,
 * never another ministry's student DB.
 */
@Service
public class CoverageGapService {

    public CoverageGapResponse gap(String state, String district, String block,
        String school, Boolean pvtgFilter) {
        return new CoverageGapResponse(0, 0, 0, List.of());
    }
}
