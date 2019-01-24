package edu.rpi.aris.proof;

import edu.rpi.aris.ast.Expression;

public class Premise {

    private boolean isSubproof;
    private Expression premise;
    private Expression[] lines;

    public Premise(Expression premise) {
        this.premise = premise;
        isSubproof = false;
    }

    public Premise(Expression assumption, Expression[] lines) {
        premise = assumption;
        this.lines = lines;
        isSubproof = true;
    }

    public boolean isSubproof() {
        return isSubproof;
    }

    public Expression getPremise() {
        return isSubproof ? null : premise;
    }

    public Expression getAssumption() {
        return isSubproof ? premise : null;
    }

    public Expression[] getSubproofLines() {
        return isSubproof ? lines : null;
    }

}
