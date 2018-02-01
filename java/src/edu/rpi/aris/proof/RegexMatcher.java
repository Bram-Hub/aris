package edu.rpi.aris.proof;

import edu.rpi.aris.rules.Rule;

import java.text.ParseException;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexMatcher {

    private static Pattern varPattern = Pattern.compile("[A-Z]+[a-z0-9]*");

    private static HashMap<String, String> attemptBind(String rule, Expression toBind, HashMap<String, String> boundVars, boolean obeyOrder) {
        String regexExp = toBind.toRegexString();
        rule = universalizeRule(rule, regexExp);
        Matcher matcher = varPattern.matcher(rule);
        TreeSet<String> vars = new TreeSet<>();
        while (matcher.find()) {
            vars.add(matcher.group());
        }
        rule = universalizeRule(rule, regexExp);
        rule = Pattern.quote(rule);
        for (String s : vars) {
            if (boundVars.containsKey(s) && obeyOrder)
                rule = rule.replaceAll(s, Pattern.quote(boundVars.get(s)));
            else
                rule = rule.replaceAll(s, "\\\\E(?<" + s + ">.+)\\\\Q");
        }
        System.out.println(vars);
        System.out.println(rule);
        Pattern rulePattern = Pattern.compile(rule);
        System.out.println(regexExp);
        Matcher ruleMatcher = rulePattern.matcher(regexExp);
        if (ruleMatcher.find()) {
            System.out.println(true);
            for (String s : vars) {
                String exp = ruleMatcher.group(s);
                boundVars.put(s, exp);
            }
        } else
            return null;
        return boundVars;
    }

    private static String universalizeRule(String rule, String exp) {
        if (rule.contains("...")) {
            Pattern pattern = Pattern.compile(Pattern.quote(rule).replaceAll(Pattern.quote("..."), "\\\\E(?<group>((.+) ?))+\\\\Q"));
            Matcher matcher = pattern.matcher(exp);

            if (matcher.find()) {
                String grouped = matcher.group("group");
                int count = grouped.length() - grouped.replace(" ", "").length() + 1;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; ++i) {
                    sb.append("A").append(i);
                    if (i + 1 != count)
                        sb.append(" ");
                }
                rule = rule.replace("...", sb.toString());
            }
        }
        return rule;
    }

    public static boolean verifyClaim(Claim claim) {
        Rule rule = claim.getRule();
        for (int i = 0; i < rule.getPremiseRules().length; ++i) {
            String[] premises = rule.getPremiseRules()[i];
            String conclusion = rule.getConclusionRules()[i][0];
            Expression[] concBindings = rule.getRegexBindings(claim.getConclusion());

        }
        return false;
    }

    public static void main(String[] args) throws ParseException {
        String logic = "¬A ∧ ¬B";
        logic = SentenceUtil.toPolishNotation(logic);
        Expression exp = new Expression(logic);
        System.out.println(universalizeRule("(& (! A) ...)", exp.toRegexString()));
//        System.out.println(logic);
//        attemptBind("(& (! A) ...)", exp, new HashMap<>(), false);
    }

}
