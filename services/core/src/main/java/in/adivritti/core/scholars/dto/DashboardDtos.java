package in.adivritti.core.scholars.dto;

import java.util.List;
import java.util.UUID;

/** Single call powering the student home screen. Java records, no Lombok. */
public final class DashboardDtos {
    private DashboardDtos() {}

    public record DashboardResponse(UUID usid, List<SchemeStatus> schemes,
        MoneySnapshot money, List<PendingAction> pendingActions) {
        public record SchemeStatus(String scheme, String schemeName, String status, String eligibility,
            String stage, String currentActor, int daysElapsed, int slaDays,
            long sanctionedAmountPaise, long paidAmountPaise) {}
        public record MoneySnapshot(long receivedPaise, long pendingPaise, long totalSanctionedPaise) {}
        public record PendingAction(String type, String message, String actionUrl) {}
    }
}
