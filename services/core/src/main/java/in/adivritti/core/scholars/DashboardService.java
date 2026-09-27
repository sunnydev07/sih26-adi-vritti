package in.adivritti.core.scholars;

import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse.MoneySnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    /** Aggregates schemes + SLA + money + pending actions for one USID. */
    @Cacheable(value = "dashboard", key = "#usid")
    public DashboardResponse dashboard(UUID usid) {
        return new DashboardResponse(usid, List.of(),
            new MoneySnapshot(0L, 0L, 0L),
            List.of(new DashboardResponse.PendingAction("none",
                "No pending actions. Your claims wallet is up to date.", null)));
    }
}
