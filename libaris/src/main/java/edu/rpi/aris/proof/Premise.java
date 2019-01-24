package edu.rpi.aris.proof;

import edu.rpi.aris.ast.Expression;

public class Premise {

    private boolean isSubproof;
    private Expression p1, p2;

    public Premise(Expression premise) {
        p1 = premise;
        isSubproof = false;
    }

    public Premise(Expression assumption, Expression conclusion) {
        p1 = assumption;
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
