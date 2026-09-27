package in.adivritti.core.identity;

import static org.junit.jupiter.api.Assertions.*;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest.IdentityRecord;
import org.junit.jupiter.api.Test;

class UsidResolverTest {

    private final UsidResolver resolver = new UsidResolver();

    private IdentityRecord rec(String name, String otr, String ref) {
        return new IdentityRecord("NSP", "EXT-1", name, "2012-05-01", "female",
            "Soma Ram", "SCH-0001", "Mandla", "1234", otr, ref);
    }

    @Test
    void deterministicMatchOnOtr() {
        assertTrue(resolver.deterministicMatch(rec("Sunita Meena", "12345678901234", null),
            rec("Sunita K. Mina", "12345678901234", null)));
    }

    @Test
    void transliterationVariantsScoreHigh() {
        double s = resolver.score(rec("Sunita Meena", null, null), rec("Sunita Mina", null, null));
        assertTrue(s > 0.9, "expected high score, got " + s);
    }

    @Test
    void differentPeopleScoreLow() {
        var a = rec("Sunita Meena", null, null);
        var b = new IdentityRecord("SFMP", "EXT-9", "Birsa Munda", "2009-11-20", "male",
            "Deva Ram", "SCH-0099", "Gumla", "9876", null, null);
        assertTrue(resolver.score(a, b) < 0.6, "expected low score");
    }

    @Test
    void borderlineGoesToHumanReview() {
        assertTrue(resolver.needsHumanReview(0.72));
        assertFalse(resolver.needsHumanReview(0.95));
    }
}
