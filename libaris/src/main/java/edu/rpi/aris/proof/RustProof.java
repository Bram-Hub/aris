package edu.rpi.aris.proof;

import edu.rpi.aris.ast.Expression;

public class RustProof {
    static { edu.rpi.aris.util.SharedObjectLoader.loadLib("libaris"); }

    protected long pointerToRustHeap;
    protected RustProof(long p) { pointerToRustHeap = p; }
    // TODO: native finalizer
    public static native RustProof createProof();
    public static native RustProof fromXml(String xml);

    @Override public native String toString();

    public native void addLine(long index, boolean isAssumption, int subProofLevel);
    public native String checkRuleAtLine(long index);
    public native void setExpressionString(long index, String expressionString);
    public native void moveCursor(long index);
}
