package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 3-stage USID resolver.
 * Stage 1 deterministic (OTR / Aadhaar ref) -&gt; confidence 1.0.
 * Stage 2 probabilistic: Jaro-Winkler on folded names + Fellegi-Sunter-ish
 * weighted agreement across 7 fields. Scores 0.60-0.90 -&gt; human adjudication.
 * Stage 3 is the officer queue (see admin exceptions endpoint).
 */
@Component
public class UsidResolver {

    private static final Map<String, Double> FIELD_WEIGHTS = Map.of(
        "name", 0.35, "dob", 0.20, "gender", 0.05, "guardian", 0.15,
        "institution", 0.10, "bankLast4", 0.05, "district", 0.10);

    /** Below this the pair is treated as "not a match" and queued for a human. */
    public static final double REVIEW_FLOOR = 0.60;
    public static final double REVIEW_CEILING = 0.90;

    /** Deterministic match if OTR or Aadhaar ref keys agree. */
    public boolean deterministicMatch(IdentityRecord a, IdentityRecord b) {
        if (sameNonBlank(a == null ? null : a.otrId(), b == null ? null : b.otrId())) return true;
        return sameNonBlank(a == null ? null : a.aadhaarRefKey(),
            b == null ? null : b.aadhaarRefKey());
    }

    public double score(IdentityRecord a, IdentityRecord b) {
        if (a == null || b == null) return 0.0;
        double s = 0.0;
        s += FIELD_WEIGHTS.get("name") * jaroWinkler(fold(a.fullName()), fold(b.fullName()));
        s += FIELD_WEIGHTS.get("dob") * agree(a.dob(), b.dob());
        s += FIELD_WEIGHTS.get("gender") * agree(a.gender(), b.gender());
        s += FIELD_WEIGHTS.get("guardian")
            * jaroWinkler(fold(nvl(a.guardianName())), fold(nvl(b.guardianName())));
        s += FIELD_WEIGHTS.get("institution") * agree(a.institutionCode(), b.institutionCode());
        s += FIELD_WEIGHTS.get("bankLast4") * agree(a.bankAccountLast4(), b.bankAccountLast4());
        s += FIELD_WEIGHTS.get("district") * agree(a.district(), b.district());
        return Math.round(s * 1000.0) / 1000.0;
    }

    public boolean needsHumanReview(double score) {
        return score >= REVIEW_FLOOR && score < REVIEW_CEILING;
    }

    // ---------------------------------------------------------------- folding

    /**
     * Indic transliteration folding: Meena / Mina / मीना collapse to one key.
     *
     * <p>Devanagari is transliterated to Latin BEFORE the ASCII filter runs. The
     * previous implementation stripped every non-{a-z} character, which reduced
     * {@code मीना} and {@code सुनीता} to the empty string. Two different Devanagari
     * names then both folded to {@code ""}, compared equal, and scored a false
     * 1.0 — i.e. the matcher declared every Indic-script record the same person.
     */
    static String fold(String name) {
        if (name == null) return "";
        String s = name;
        if (hasDevanagari(s)) {
            // Collapse doubled vowels only for transliterated output, so that
            // Latin spellings like "Meena" are left intact for variant folding.
            s = collapseVowelRuns(transliterateDevanagari(s));
        }
        s = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return s
            .replace("meena", "mina")
            .replace("suneeta", "sunita")
            .replace("choudhary", "chaudhary")
            .replace("chaudhari", "chaudhary");
    }

    private static boolean hasDevanagari(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) return true;
        }
        return false;
    }

    /**
     * Consonant grapheme -&gt; Latin stem. The inherent "a" is supplied by the caller.
     *
     * <p>Keys are {@code String}, not {@code char}: a nukta letter (क़) and a
     * conjunct (क्ष) are each more than one Unicode code point, so they cannot be
     * expressed as Java character literals.
     */
    private static final Map<String, String> CONSONANTS = Map.ofEntries(
        Map.entry("क", "k"), Map.entry("ख", "kh"), Map.entry("ग", "g"), Map.entry("घ", "gh"),
        Map.entry("ङ", "ng"), Map.entry("च", "ch"), Map.entry("छ", "chh"), Map.entry("ज", "j"),
        Map.entry("झ", "jh"), Map.entry("ञ", "ny"), Map.entry("ट", "t"), Map.entry("ठ", "th"),
        Map.entry("ड", "d"), Map.entry("ढ", "dh"), Map.entry("ण", "n"), Map.entry("त", "t"),
        // ध (dental dh) is a different code point from ढ (retroflex dh) above.
        Map.entry("थ", "th"), Map.entry("द", "d"), Map.entry("ध", "dh"), Map.entry("न", "n"),
        Map.entry("प", "p"), Map.entry("फ", "ph"), Map.entry("ब", "b"), Map.entry("भ", "bh"),
        Map.entry("म", "m"), Map.entry("य", "y"), Map.entry("र", "r"), Map.entry("ल", "l"),
        Map.entry("व", "v"), Map.entry("श", "sh"), Map.entry("ष", "sh"), Map.entry("स", "s"),
        Map.entry("ह", "h"), Map.entry("ळ", "l"), Map.entry("ऱ", "r"), Map.entry("ऴ", "l"),
        // Nukta letters (consonant + U+093C).
        Map.entry("क़", "q"), Map.entry("ख़", "kh"), Map.entry("ग़", "gh"),
        Map.entry("ज़", "z"), Map.entry("ड़", "r"), Map.entry("ढ़", "rh"),
        Map.entry("फ़", "f"), Map.entry("य़", "y"),
        // Conjuncts.
        Map.entry("क्ष", "ksh"), Map.entry("त्र", "tr"),
        Map.entry("ज्ञ", "gy"), Map.entry("श्र", "shr"));

    /** Dependent vowel sign -&gt; replaces the consonant's inherent "a". */
    private static final Map<String, String> MATRAS = Map.ofEntries(
        Map.entry("ा", "a"), Map.entry("ि", "i"), Map.entry("ी", "i"), Map.entry("ु", "u"),
        Map.entry("ू", "u"), Map.entry("ृ", "ri"), Map.entry("े", "e"), Map.entry("ै", "ai"),
        Map.entry("ो", "o"), Map.entry("ौ", "au"), Map.entry("ॉ", "o"), Map.entry("ॅ", "e"),
        Map.entry("ऄ", "e"), Map.entry("ऍ", "e"), Map.entry("ऑ", "o"));

    private static final String VIRAMA = "्";
    private static final String ANUSVARA = "ं";
    private static final String VISARGA = "ः";
    private static final String NUKTA = "़";

    private static String transliterateDevanagari(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            // Longest grapheme wins: conjuncts (3 cp) before nukta letters (2 cp)
            // before bare consonants/matras (1 cp). The length is clamped first —
            // bounding it in the loop condition instead would exit the loop
            // immediately for a match that needs fewer code points.
            String grapheme = null;
            String stem = null;
            int maxLen = Math.min(3, s.length() - i);
            for (int len = maxLen; len >= 1; len--) {
                String candidate = s.substring(i, i + len);
                String found = CONSONANTS.get(candidate);
                if (found != null) {
                    grapheme = candidate;
                    stem = found;
                    break;
                }
            }
            if (grapheme == null) {
                String one = String.valueOf(s.charAt(i));
                if (ANUSVARA.equals(one)) {
                    out.append('n');
                } else if (VISARGA.equals(one)) {
                    out.append('h');
                } else if (VIRAMA.equals(one) || NUKTA.equals(one)) {
                    // Unmatched joiner: swallow it and drop the inherent vowel.
                    dropTrailingInherentVowel(out);
                } else if (MATRAS.containsKey(one)) {
                    // Stray matra with no preceding consonant.
                    replaceTrailingInherentVowel(out, MATRAS.get(one));
                } else if (Character.isWhitespace(s.charAt(i))) {
                    out.append(' ');
                } else {
                    // Latin or unlisted script passes through untouched.
                    out.append(s.charAt(i));
                }
                i++;
                continue;
            }

            out.append(stem);
            i += grapheme.length();
            // A following matra replaces the inherent vowel.
            if (i < s.length()) {
                String next = String.valueOf(s.charAt(i));
                String vowel = MATRAS.get(next);
                if (vowel != null) {
                    out.append(vowel);
                    i++;
                    continue;
                }
            }
            out.append('a');
        }
        return out.toString();
    }

    private static void replaceTrailingInherentVowel(StringBuilder out, String vowel) {
        if (out.length() > 0 && out.charAt(out.length() - 1) == 'a') {
            out.setCharAt(out.length() - 1, vowel.charAt(0));
            for (int i = 1; i < vowel.length(); i++) out.append(vowel.charAt(i));
        } else {
            out.append(vowel);
        }
    }

    private static void dropTrailingInherentVowel(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) == 'a') {
            out.setLength(out.length() - 1);
        }
    }

    /** "miinaa" -&gt; "mina". Only applied to transliterated (Devanagari) text. */
    private static String collapseVowelRuns(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean vowel = "aeiou".indexOf(c) >= 0;
            if (vowel && out.length() > 0 && out.charAt(out.length() - 1) == c) continue;
            out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------- comparison

    private static double agree(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        return x.equals(y) ? 1.0 : 0.0;
    }

    private static boolean sameNonBlank(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return !x.isEmpty() && x.equals(y);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * Jaro-Winkler similarity in [0,1].
     *
     * <p>Two fixes over the original: the match window is floored at 0 (it went
     * negative for very short strings, so nothing ever matched), and the
     * transposition scan is bounds-checked (it could run off the end of the flag
     * array and throw). Emptiness is now checked <em>before</em> equality, so two
     * blank inputs score 0.0 instead of 1.0.
     */
    static double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int matchDist = Math.max(Math.max(s1.length(), s2.length()) / 2 - 1, 0);
        boolean[] m1 = new boolean[s1.length()];
        boolean[] m2 = new boolean[s2.length()];
        int matches = 0;
        for (int i = 0; i < s1.length(); i++) {
            int lo = Math.max(0, i - matchDist);
            int hi = Math.min(i + matchDist + 1, s2.length());
            for (int j = lo; j < hi; j++) {
                if (!m2[j] && s1.charAt(i) == s2.charAt(j)) {
                    m1[i] = true;
                    m2[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) return 0.0;

        int t = 0;
        int k = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (!m1[i]) continue;
            while (k < m2.length && !m2[k]) k++;
            if (k >= m2.length) break;
            if (s1.charAt(i) != s2.charAt(k)) t++;
            k++;
        }

        double m = matches;
        double jaro = (m / s1.length() + m / s2.length() + (m - t / 2.0) / m) / 3.0;
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) != s2.charAt(i)) break;
            prefix++;
        }
        double score = jaro + prefix * 0.1 * (1 - jaro);
        return Math.min(1.0, Math.round(score * 1000.0) / 1000.0);
    }
}
