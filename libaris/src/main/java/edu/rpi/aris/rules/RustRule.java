package edu.rpi.aris.rules;

import java.util.*;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;

public class RustRule extends Rule {
    static { edu.rpi.aris.util.SharedObjectLoader.loadLib("liblibaris_rs"); }

    protected long pointerToRustHeap;
    protected RustRule(long p) { pointerToRustHeap = p; }
    // TODO: native finalizer
    public static native RustRule fromRule(RuleList x);

    @Override public native String toString();

    // TODO: rule metadata
    public String getName() { return null; }
    public String getSimpleName() { return null; }
    public Type[] getRuleType() { return null; }

    // TODO: autofill
    public boolean canAutoFill() { return false; }
    protected ArrayList<String> getAutoFill(Premise[] premises) { return null; }

    public native int requiredPremises();
    public native boolean canGeneralizePremises();
    public native int subProofPremises();

    protected String verifyClaim(Expression conclusion, Premise[] premises) { return null; }
}
