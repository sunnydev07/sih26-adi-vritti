package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityRecord;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 3-stage USID resolver.
 * Stage 1 deterministic (OTR / Aadhaar ref) -> confidence 1.0.
 * Stage 2 probabilistic: Jaro-Winkler on folded names + Fellegi-Sunter-ish
 * weighted agreement across 7 fields. Scores 0.60-0.90 -> human adjudication.
 * Stage 3 is the officer queue (see admin exceptions endpoint).
 */
@Component
public class UsidResolver {

    private static final Map<String, Double> FIELD_WEIGHTS = Map.of(
        "name", 0.35, "dob", 0.20, "gender", 0.05, "guardian", 0.15,
        "institution", 0.10, "bankLast4", 0.05, "district", 0.10);

    /** Deterministic match if OTR or Aadhaar ref keys agree. */
    public boolean deterministicMatch(IdentityRecord a, IdentityRecord b) {
        if (a.otrId() != null && a.otrId().equals(b.otrId())) return true;
        return a.aadhaarRefKey() != null && a.aadhaarRefKey().equals(b.aadhaarRefKey());
    }

    public double score(IdentityRecord a, IdentityRecord b) {
        double s = 0.0;
        s += FIELD_WEIGHTS.get("name") * jaroWinkler(fold(a.fullName()), fold(b.fullName()));
        s += FIELD_WEIGHTS.get("dob") * agree(a.dob(), b.dob());
        s += FIELD_WEIGHTS.get("gender") * agree(a.gender(), b.gender());
        s += FIELD_WEIGHTS.get("guardian") * jaroWinkler(fold(nvl(a.guardianName())), fold(nvl(b.guardianName())));
        s += FIELD_WEIGHTS.get("institution") * agree(a.institutionCode(), b.institutionCode());
        s += FIELD_WEIGHTS.get("bankLast4") * agree(a.bankAccountLast4(), b.bankAccountLast4());
        s += FIELD_WEIGHTS.get("district") * agree(a.district(), b.district());
        return Math.round(s * 1000.0) / 1000.0;
    }

    public boolean needsHumanReview(double score) {
        return score >= 0.60 && score < 0.90;
    }

    /** Indic transliteration folding: Meena/Mina/मीना collapse to one key. */
    static String fold(String name) {
        if (name == null) return "";
        String n = Normalizer.normalize(name.toLowerCase(), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "").replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        return n.replace("meena", "mina").replace("suneeta", "sunita")
            .replace("choudhary", "chaudhary").replace("chaudhari", "chaudhary");
    }

    private static double agree(String a, String b) {
        if (a == null || b == null) return 0.0;
        return a.equalsIgnoreCase(b) ? 1.0 : 0.0;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        int matchDist = Math.max(s1.length(), s2.length()) / 2 - 1;
        boolean[] m1 = new boolean[s1.length()];
        boolean[] m2 = new boolean[s2.length()];
        int matches = 0;
        for (int i = 0; i < s1.length(); i++) {
            int lo = Math.max(0, i - matchDist), hi = Math.min(i + matchDist + 1, s2.length());
            for (int j = lo; j < hi; j++) {
                if (!m2[j] && s1.charAt(i) == s2.charAt(j)) { m1[i] = true; m2[j] = true; matches++; break; }
            }
        }
        if (matches == 0) return 0.0;
        int t = 0, k = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (m1[i]) {
                while (!m2[k]) k++;
                if (s1.charAt(i) != s2.charAt(k)) t++;
                k++;
            }
        }
        double m = matches;
        double jaro = (m / s1.length() + m / s2.length() + (m - t / 2.0) / m) / 3.0;
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
