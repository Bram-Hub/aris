package edu.rpi.aris.rules;

import edu.rpi.aris.ast.Expression;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class Rule {
    static { edu.rpi.aris.util.SharedObjectLoader.loadLib("liblibaris_rs"); }

    protected long pointerToRustHeap;
    protected Rule(long p) { pointerToRustHeap = p; }
    // TODO: native finalizer
    public static native Rule fromRule(RuleList x);

    @Override public native String toString();

    // TODO: rule metadata
    public String getName() { return null; }
    public String getSimpleName() { return null; }
    public Type[] getRuleType() { return null; }

    // TODO: autofill
    public boolean canAutoFill() { return false; }
    protected ArrayList<String> getAutoFill(Premise[] premises) { return null; }

    public ArrayList<String> getAutoFillCandidates(Premise[] premises) {
        if (premises == null)
            return null;
        if (!canGeneralizePremises() && premises.length != requiredPremises())
            return null;
        int spPremises = 0;
        for (Premise p : premises)
            if (p.isSubproof())
                spPremises++;
        if (!canGeneralizePremises() && spPremises != subProofPremises())
            return null;
        return getAutoFill(premises);
    }

    public native int requiredPremises();
    public native boolean canGeneralizePremises();
    public native int subProofPremises();

    public String verifyClaim(Expression conclusion, Premise[] premises) { return null; }

    public enum Type {

        INTRO("Introduction"),
        ELIM("Elimination"),
        EQUIVALENCE("Equivalence"),
        INFERENCE("Inference"),
        PREDICATE("Predicate");

        public final String name;

        Type(String name) {
            this.name = name;
        }

    }
}
