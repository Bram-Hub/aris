package edu.rpi.aris.proof;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexMatcher {

    public static final char REGEX_SPACE = '~';
    private static Pattern varPattern = Pattern.compile("[A-Z]+[a-z0-9]*");

    private static HashMap<String, String> attemptBind(String rule, Expression toBind, HashMap<String, String> boundVars, boolean obeyOrder, boolean allowBinding) {
        int depth;
        if (rule.contains("...")) {
            depth = 0;
            label:
            for (int i = 0; i < rule.length(); ++i) {
                char c = rule.charAt(i);
                switch (c) {
                    case SentenceUtil.OP:
                        depth++;
                        break;
                    case SentenceUtil.CP:
                        depth--;
                        break;
                    case '.':
                        break label;
                }
            }
            rule = toRegexString(rule, depth);
        } else
            depth = rule.length() - rule.replace(String.valueOf(SentenceUtil.OP), "").length();
        String regexExp = toRegexString(toBind.toString(), depth);
        rule = universalizeRule(rule, regexExp);
        Matcher matcher = varPattern.matcher(rule);
        TreeSet<String> vars = new TreeSet<>();
        while (matcher.find()) {
            vars.add(matcher.group());
        }
        rule = Pattern.quote(rule);
        HashSet<String> remove = new HashSet<>();
        for (String s : vars) {
            if (boundVars.containsKey(s) && obeyOrder) {
                rule = rule.replaceAll(s, boundVars.get(s));
                remove.add(s);
            } else {
                rule = rule.replaceFirst(s, "\\\\E(?<" + s + ">.{1,4096})\\\\Q");
                rule = rule.replace(' ' + s, " \\<" + s + ">");
            }
        }
        vars.removeAll(remove);
        Pattern rulePattern = Pattern.compile(rule);
        Matcher ruleMatcher = rulePattern.matcher(regexExp);
        if (ruleMatcher.matches()) {
            if (obeyOrder && !allowBinding)
                return boundVars;
            for (String s : vars) {
                String exp = ruleMatcher.group(s).replace('~', ' ');
                if (obeyOrder) {
                    if (boundVars.containsKey(s)) {
                        if (!boundVars.get(s).equals(exp)) {
                            return null;
                        }
                    } else
                        boundVars.put(s, exp);
                } else {
                    if (!boundVars.containsKey(exp)) {
                        if (allowBinding)
                            boundVars.put(exp, exp);
                        else
                            return null;
                    }
                }
            }
        } else
            return null;
        return boundVars;
    }

    private static String universalizeRule(String rule, String exp) {
        if (rule.contains("...")) {
            int formatEnd = rule.indexOf("...") - 1;
            int formatStart = formatEnd;
            int parenDepth = 0;
            for (int i = formatEnd - 1; i >= 0; --i) {
                char c = rule.charAt(i);
                if (c == SentenceUtil.CP)
                    parenDepth++;
                else if (c == SentenceUtil.OP)
                    parenDepth--;
                else if (parenDepth == 0 && c == ' ') {
                    formatStart = i + 1;
                    break;
                }
                if (parenDepth < 0) {
                    while (c != ' ') {
                        ++i;
                        c = rule.charAt(i);
                    }
                    formatStart = i + 1;
                    break;
                }
            }
            String format = rule.substring(formatStart, formatEnd);
            Matcher m = varPattern.matcher(format);
            if (!m.find())
                return rule;
            String formatVar = m.group();
            rule = rule.replace(" " + format, "");
            Pattern pattern = Pattern.compile(Pattern.quote(rule).replaceAll(Pattern.quote("..."), "\\\\E(?<group>((.+) ?))+\\\\Q"));
            Matcher matcher = pattern.matcher(exp);
            if (matcher.find()) {
                String grouped = matcher.group("group");
                int count = grouped.length() - grouped.replace(" ", "").length() + 1;
                if (count > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < count; ++i) {
                        sb.append(format.replace(formatVar, "A" + i));
                        if (i + 1 != count)
                            sb.append(" ");
                    }
                    rule = rule.replace("...", sb.toString());
                } else {
                    rule = format.replace(formatVar, "A");
                }
            } else {
                rule = format.replace(formatVar, "A");
            }
        }
        return rule;
    }

    private static String toRegexString(String str, int target) {
        int depth = 0;
        char[] arr = str.toCharArray();
        for (int i = 0; i < arr.length; ++i) {
            char c = arr[i];
            switch (c) {
                case SentenceUtil.OP:
                    depth++;
                    break;
                case SentenceUtil.CP:
                    depth--;
                    break;
                case ' ':
                    if (depth > target)
                        arr[i] = REGEX_SPACE;
                    break;
            }
        }
        return String.valueOf(arr);
    }

//    public static boolean verifyClaim(Claim claim) {
//        RuleList rule = claim.getRule();
//        for (int i = 0; i < rule.premises.length; ++i) {
//            String[] premisesRules = rule.premises[i];
//            String conclusionRule = rule.conclusions[i];
//            Premise[] premises = claim.getPremises();
//            HashMap<String, String> map = new HashMap<>();
//            boolean valid = true;
//            if (rule.bindConclusionFirst)
//                if (attemptBind(conclusionRule, claim.getConclusion(), map, rule.obeyOrder, true) == null) {
//                    valid = false;
//                }
//            if (valid) {
//                ArrayList<String> pRules = Arrays.stream(premisesRules).collect(Collectors.toCollection(ArrayList::new));
//                for (Premise p : premises) {
//                    String remove = null;
//                    for (int j = 0; j < pRules.size(); ++j) {
//                        if (attemptBind(pRules.get(j), p.getPremise(), map, rule.obeyOrder, !rule.bindConclusionFirst && j == 0) != null) {
//                            remove = pRules.get(j);
//                            break;
//                        } else if (j == pRules.size() - 1)
//                            valid = false;
//                    }
//                    if (remove != null)
//                        pRules.remove(remove);
//                }
//            }
//            if (valid && !rule.bindConclusionFirst)
//                valid = attemptBind(conclusionRule, claim.getConclusion(), map, rule.obeyOrder, false) != null;
//            if (valid)
//                return true;
//        }
//        return false;
//    }

//    public static void main(String[] args) throws ParseException {
//        String logic = "¬A ∧ ¬B";
//        logic = SentenceUtil.toPolishNotation(logic);
//        Expression exp = new Expression("(<-> A B)");
//        Claim c = new Claim(new Expression("B"), new Premise[]{new Premise(exp), new Premise(new Expression("A"))}, RuleList.BICONDITIONAL);
//        System.out.println(verifyClaim(c));
//    }

}
