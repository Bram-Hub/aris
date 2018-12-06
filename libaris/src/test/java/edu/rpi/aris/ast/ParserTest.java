import edu.rpi.aris.ast.*;

import org.junit.Test;

public class ParserTest {
    String[] should_parse = {
        "a",
        "a & b & c",
        "exists x, (Tet(x) & SameCol(x, b)) -> ~forall x, (Tet(x) -> LeftOf(x, b))",
        "exists x, exists y, (Cube(x) & Tet(y) & LeftOf(x, y))",
        "∀x,∀y, ∃z, ((Cube(x) ∧ Tet(y) ∧ LeftOf(x, y)) -> (Dodec(z) ∧ BackOf(x, z)))",
    };
    String[] should_fail = {
        "a & b | c",
        "a && b",
        "forall ()",
    };

    void test_cases(String[] toParse, boolean test_for_null) {
        for(String s : toParse) {
            System.out.printf("Attempting to parse \"%s\"\n", s);
            Expression e = ASTConstructor.parse(s);
            assert(test_for_null ^ (e == null));
            System.out.printf("Parsed version of \"%s\" is \"%s\"\n", s, e);
        }
    }
    @Test
    public void test_should_parse() {
        test_cases(should_parse, true);
    }
    @Test
    public void test_should_fail() {
        test_cases(should_fail, false);
    }
}
