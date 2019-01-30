import edu.rpi.aris.rules.*;

import org.junit.Test;


public class RustRuleTest {
    @Test
    public void test_rust_rules() {
        RustRule conjunction = RustRule.fromRule(RuleList.CONJUNCTION);
        System.out.printf("conjunction %s %d %b %d\n", conjunction, conjunction.requiredPremises(), conjunction.canGeneralizePremises(), conjunction.subProofPremises());
    }
}
