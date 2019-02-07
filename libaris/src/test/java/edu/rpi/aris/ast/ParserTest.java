import edu.rpi.aris.ast.*;

import org.junit.Test;

public class ParserTest {
    String[] should_parse = {
        "a",
        "a & b & c",
        "exists x, (Tet(x) & SameCol(x, b)) -> ~forall x, (Tet(x) -> LeftOf(x, b))",
        "exists x, exists y, (Cube(x) & Tet(y) & LeftOf(x, y))",
        "∀x,∀y, ∃z, ((Cube(x) ∧ Tet(y) ∧ LeftOf(x, y)) -> (Dodec(z) ∧ BackOf(x, z)))",
        "forall p, exists a, forall x, (in(x, a) <-> p(x))", // axiom of comprehension as a second-order formula
        "forall a, forall b, ((forall x, in(x,a) <-> in(x,b)) -> eq(a,b))", // axiom of extensionality
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
            if(e != null) { System.out.printf("Rust stringification of Java parse: %s\n", e.toDebugString()); }
        }
    }

    void test_cases_rust(String[] toParse, boolean test_for_null) {
        for(String s : toParse) {
            System.out.printf("Attempting to parse \"%s\" via Rust\n", s);
            Expression e = Expression.parseViaRust(s);
            assert(test_for_null ^ (e == null));
            System.out.printf("Parsed version of \"%s\" via Rust is \"%s\"\n", s, e);
            if(e != null) { System.out.printf("Rust stringification of Rust parse: %s\n", e.toDebugString()); }
        }
    }
    @Test
    public void test_should_parse() {
        test_cases(should_parse, true);
        test_cases_rust(should_parse, true);
    }
    @Test
    public void test_should_fail() {
        test_cases(should_fail, false);
        test_cases_rust(should_fail, false);
    }
}
