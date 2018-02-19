package edu.rpi.aris.proof;

public class Premise {

    private boolean isSubproof;
    private Expression p1, p2;

    public Premise(Expression premis) {
        p1 = premis;
        isSubproof = false;
    }

    public Premise(Expression assumtion, Expression conclusion) {
        p1 = assumtion;
        p2 = conclusion;
        isSubproof = true;
    }

    public boolean isSubproof() {
        return isSubproof;
    }

    public Expression getPremise() {
        return isSubproof ? null : p1;
    }

    public Expression getAssumption() {
        return isSubproof ? p1 : null;
    }

    public Expression getConclusion() {
        return isSubproof ? p2 : null;
    }

}
