package in.adivritti.core.scholars;

import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.ConsentGate;
import in.adivritti.core.disbursement.entity.Disbursement;
import in.adivritti.core.disbursement.repository.DisbursementRepository;
import in.adivritti.core.eligibility.EligibilityService;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse.MoneySnapshot;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse.PendingAction;
import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse.SchemeStatus;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ApplicationRepository applications;
    private final DisbursementRepository disbursements;
    private final DeficiencyRepository deficiencies;
    private final EligibilityService eligibility;
    private final SlaCalculator sla;
    private final ConsentGate consent;

    public DashboardService(ApplicationRepository applications,
        DisbursementRepository disbursements, DeficiencyRepository deficiencies,
        EligibilityService eligibility, SlaCalculator sla, ConsentGate consent) {
        this.applications = applications;
        this.disbursements = disbursements;
        this.deficiencies = deficiencies;
        this.eligibility = eligibility;
        this.sla = sla;
        this.consent = consent;
    }

    /** Aggregates schemes + SLA + money + pending actions for one USID. */
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID usid) {
        if (usid == null) throw new NotFoundException("SCHOLAR_NOT_FOUND", "Scholar not found");
        consent.requireConsent(usid, "application_submission", "dashboard");

        List<Application> apps = applications.findByUsidOrderByCreatedAtDesc(usid);
        List<Disbursement> pays = disbursements.findByUsidOrderByScheme(usid);

        // Keep the most recent application per scheme.
        Map<String, Application> byScheme = new LinkedHashMap<>();
        for (Application a : apps) byScheme.putIfAbsent(a.scheme, a);

        MoneySnapshot money = moneySnapshot(pays);
        Map<String, String> verdicts = eligibilityVerdicts(usid);

        List<SchemeStatus> schemes = new ArrayList<>();
        for (Map.Entry<String, Application> e : byScheme.entrySet()) {
            Application a = e.getValue();
            long days = sla.daysElapsed(
                a.createdAt == null ? ZonedDateTime.now() : a.createdAt);
            schemes.add(new SchemeStatus(
                a.scheme, a.scheme, deriveStatus(a.stage),
                verdicts.getOrDefault(normalizeScheme(a.scheme), "unknown"),
                a.stage, a.currentActor, (int) days, sla.slaDays(a.stage),
                sumFor(pays, a.scheme, true), sumFor(pays, a.scheme, false)));
        }

        return new DashboardResponse(usid, schemes, money, pendingActions(usid));
    }

    /**
     * Verdict labels are filename-form ({@code pre-matric}) while stored
     * applications carry the feed form ({@code PRE_MATRIC}). Without
     * normalisation the lookup misses and every seeded scheme renders
     * eligibility {@code "unknown"} — a value the contract enum does not even
     * allow.
     */
    static String normalizeScheme(String scheme) {
        return scheme == null ? null
            : scheme.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * Derive the contract {@code SchemeStatus.status} from the application stage.
     * Previously every scheme was hard-coded to {@code "in_progress"} regardless of
     * stage, against a contract enum of
     * {@code eligible|applied|in_verification|approved|disbursed|deficient|not_eligible}.
     * {@code deficient} is deliberately not derived here: deficiency rows carry no
     * scheme linkage yet, so per-scheme deficiency needs schema work (follow-up).
     * Unknown stages map to {@code in_verification} — mid-pipeline work, never a
     * terminal claim about the outcome.
     */
    static String deriveStatus(String stage) {
        if (stage == null) return "in_verification";
        return switch (stage) {
            case "submitted" -> "applied";
            case "institute_verification", "district_nodal", "state_dept", "ministry" ->
                "in_verification";
            case "pfms_payment" -> "approved";
            case "disbursed" -> "disbursed";
            default -> "in_verification";
        };
    }

    private MoneySnapshot moneySnapshot(List<Disbursement> pays) {
        long sanctioned = pays.stream().mapToLong(d -> d.sanctionedAmountPaise).sum();
        long paid = pays.stream().mapToLong(d -> d.paidAmountPaise).sum();
        return new MoneySnapshot(paid, Math.max(0, sanctioned - paid), sanctioned);
    }

    private static long sumFor(List<Disbursement> pays, String scheme, boolean sanctioned) {
        return pays.stream()
            .filter(d -> scheme.equals(d.scheme))
            .mapToLong(d -> sanctioned ? d.sanctionedAmountPaise : d.paidAmountPaise)
            .sum();
    }

    private Map<String, String> eligibilityVerdicts(UUID usid) {
        try {
            EligibilityResponse response = eligibility.evaluate(
                new EligibilityRequest(usid, null));
            Map<String, String> out = new LinkedHashMap<>();
            for (EligibilityResponse.SchemeVerdict v : response.verdicts()) {
                out.put(v.scheme(), v.verdict());
            }
            return out;
        } catch (RuntimeException e) {
            // Eligibility is advisory on the home screen; a rules problem must not
            // take the whole dashboard down.
            return Map.of();
        }
    }

    private List<PendingAction> pendingActions(UUID usid) {
        List<PendingAction> actions = new ArrayList<>();
        deficiencies.findByUsidAndStatusOrderByCreatedAtDesc(usid, "open")
            .forEach(d -> actions.add(new PendingAction(
                d.type, d.message, "/applications/" + d.applicationId + "/timeline")));
        if (actions.isEmpty()) {
            actions.add(new PendingAction("none",
                "No pending actions. Your claims wallet is up to date.", null));
        }
        return actions;
    }
}
