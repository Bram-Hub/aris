package edu.rpi.aris.proof;

import edu.rpi.aris.rules.Rule;

public class Claim {

    private Expression conclusion;
    private Premise[] premises;
    private Rule rule;

    public Claim(Expression conclusion, Premise[] premises, Rule rule) {
        this.conclusion = conclusion;
        if (premises == null)
            premises = new Premise[0];
        this.premises = premises;
        this.rule = rule;
    }

    public String isValidClaim() {
        if (rule == null)
            return "No rule specified";
        if (conclusion == null)
            return "No conclusion specified";
        return rule.verifyClaim(this);
    }

    public Expression getConclusion() {
        return conclusion;
    }

    public Premise[] getPremises() {
        return premises;
    }
}
