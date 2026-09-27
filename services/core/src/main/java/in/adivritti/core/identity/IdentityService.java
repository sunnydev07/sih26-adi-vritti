package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.DuplicateFlag;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.LinkedSystemRecord;
import in.adivritti.core.identity.entity.Scholar;
import in.adivritti.core.identity.entity.ScholarSystemLink;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {

    private final UsidResolver resolver;

    public IdentityService(UsidResolver resolver) {
        this.resolver = resolver;
    }

    public IdentityResolveResponse resolve(IdentityResolveRequest req) {
        if (req.records() == null || req.records().isEmpty()) {
            throw new IllegalArgumentException("At least one identity record is required");
        }
        var first = req.records().get(0);
        List<LinkedSystemRecord> links = new ArrayList<>();
        double minScore = 1.0;
        boolean human = false;
        for (var r : req.records()) {
            boolean det = resolver.deterministicMatch(first, r);
            double score = det ? 1.0 : resolver.score(first, r);
            minScore = Math.min(minScore, score);
            if (!det && resolver.needsHumanReview(score)) human = true;
            links.add(new LinkedSystemRecord(
                r.systemName(), r.externalId(), score, det ? "deterministic" : "probabilistic"));
        }
        // Scholar persistence wiring goes here (repositories); USID minted per resolution group.
        Scholar scholar = new Scholar();
        scholar.usid = UUID.randomUUID();
        List<ScholarSystemLink> persisted = links.stream().map(l -> {
            ScholarSystemLink s = new ScholarSystemLink();
            s.usid = scholar.usid;
            s.systemName = l.systemName();
            s.externalId = l.externalId();
            s.matchConfidence = l.confidence();
            s.resolutionMethod = l.resolutionMethod();
            return s;
        }).toList();
        return new IdentityResolveResponse(
            scholar.usid, links, minScore, human, new DuplicateFlag(false, List.of()));
    }
}
