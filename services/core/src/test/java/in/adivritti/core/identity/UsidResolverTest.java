package in.adivritti.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import org.junit.jupiter.api.Test;

class UsidResolverTest {

    private final UsidResolver resolver = new UsidResolver();

    private IdentityRecord rec(String name, String otr, String ref) {
        return IdentityRecord.of("NSP", "EXT-1", name, "2012-05-01", "female",
            "Soma Ram", "SCH-0001", "Mandla", "1234", otr, ref);
    }

    @Test
    void deterministicMatchOnOtr() {
        assertTrue(resolver.deterministicMatch(rec("Sunita Meena", "12345678901234", null),
            rec("Sunita K. Mina", "12345678901234", null)));
    }

    @Test
    void deterministicMatchRequiresNonBlankValue() {
        // Two blank OTRs must not count as a match, or every record without an OTR
        // would resolve deterministically to confidence 1.0.
        assertFalse(resolver.deterministicMatch(rec("A", "  ", null), rec("B", "  ", null)));
        assertFalse(resolver.deterministicMatch(rec("A", null, null), rec("B", null, null)));
    }

    @Test
    void transliterationVariantsScoreHigh() {
        double s = resolver.score(rec("Sunita Meena", null, null), rec("Sunita Mina", null, null));
        assertTrue(s > 0.9, "expected high score, got " + s);
    }

    @Test
    void differentPeopleScoreLow() {
        var a = rec("Sunita Meena", null, null);
        var b = IdentityRecord.of("SFMP", "EXT-9", "Birsa Munda", "2009-11-20", "male",
            "Deva Ram", "SCH-0099", "Gumla", "9876", null, null);
        assertTrue(resolver.score(a, b) < 0.6, "expected low score");
    }

    @Test
    void borderlineGoesToHumanReview() {
        assertTrue(resolver.needsHumanReview(0.72));
        assertFalse(resolver.needsHumanReview(0.95));
        assertFalse(resolver.needsHumanReview(0.10));
    }

    // ------------------------------------------------------------- folding

    @Test
    void devanagariFoldsToLatinInsteadOfEmpty() {
        // Previously the ASCII filter reduced Devanagari to "", so two DIFFERENT
        // Indic names compared equal and scored a false 1.0.
        assertFalse(UsidResolver.fold("मीना").isEmpty(),
            "Devanagari must not fold to the empty string");
        assertEquals(UsidResolver.fold("Meena"), UsidResolver.fold("मीना"));
    }

    @Test
    void distinctDevanagariNamesDoNotCollapse() {
        double a = UsidResolver.jaroWinkler(UsidResolver.fold("मीना"), UsidResolver.fold("सुनीता"));
        double b = UsidResolver.jaroWinkler(UsidResolver.fold("मीना"), UsidResolver.fold("फूलो"));
        assertNotEquals(a, b, "different Devanagari names must not fold to the same key");
    }

    @Test
    void latinSpellingIsNotVowelCollapsed() {
        // The vowel-run collapse applies only to transliterated text. If it also
        // ran on Latin input, "Meena" would become "mena" and never reach the
        // "meena -> mina" mapping that makes Meena/Mina compare equal.
        assertEquals("mina", UsidResolver.fold("Meena"));
        assertEquals("minah", UsidResolver.fold("Meenah"));
        assertNotEquals(UsidResolver.fold("Meena"), UsidResolver.fold("Mena"));
    }

    @Test
    void fieldComparisonIgnoresSurroundingWhitespaceAndCase() {
        var a = rec("Sunita Meena", null, null);
        var b = IdentityRecord.of("NSP", "EXT-2", "Sunita Meena", "2012-05-01", "Female",
            "Soma Ram", "SCH-0001", "  mandla ", "1234", null, null);
        assertTrue(resolver.score(a, b) > 0.95, "whitespace/case must not lower the score");
    }

    // ------------------------------------------------------- jaro-winkler

    @Test
    void twoEmptyStringsScoreZeroNotOne() {
        // s1.equals(s2) was checked before the emptiness guard, so "" vs "" returned
        // 1.0 and made every nameless record a match for every other.
        assertEquals(0.0, UsidResolver.jaroWinkler("", ""));
        assertEquals(0.0, UsidResolver.jaroWinkler("", "abc"));
    }

    @Test
    void nullInputsAreSafe() {
        assertEquals(0.0, UsidResolver.jaroWinkler(null, "abc"));
        assertEquals(0.0, UsidResolver.jaroWinkler("abc", null));
    }

    @Test
    void singleCharacterStringsDoNotThrow() {
        // The match window used to be -1 for length-1 strings, and the transposition
        // scan walked off the end of the flag array.
        for (String a : new String[] {"a", "b", "z", ""}) {
            for (String b : new String[] {"a", "b", "y", ""}) {
                double s = UsidResolver.jaroWinkler(a, b);
                assertTrue(s >= 0.0 && s <= 1.0, "score out of range for '" + a + "'/'" + b + "'");
            }
        }
    }

    @Test
    void identicalStringsScoreOne() {
        assertEquals(1.0, UsidResolver.jaroWinkler("mandla", "mandla"));
    }

    @Test
    void prefixBonusIsCappedAndBounded() {
        double s = UsidResolver.jaroWinkler("mandla", "mandala");
        assertTrue(s > 0.9 && s <= 1.0, "expected a high bounded score, got " + s);
    }

    @Test
    void disjointStringsScoreLow() {
        assertTrue(UsidResolver.jaroWinkler("abc", "xyz") < 0.5);
    }
}
