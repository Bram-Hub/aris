package edu.rpi.aris.server;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.assign.AutoGrader;
import edu.rpi.aris.assign.Problem;
import edu.rpi.aris.proof.ArisProofProblem;
import edu.rpi.aris.proof.Goal;
import edu.rpi.aris.proof.Line;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.RuleList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

public class ArisGrader implements AutoGrader<LibAris> {

    private ArrayList<Goal> getGoals(Proof p) {
        ArrayList<Goal> goals = new ArrayList<>();
        for (int i = 0; i < p.getNumGoals(); ++i) {
            Goal g = p.getGoal(i);
            if (g.getGoalString().trim().length() > 0)
                goals.add(g);
        }
        goals.sort(Comparator.comparing(Goal::getGoalString));
        return goals;
    }

    private ArrayList<Line> getPremises(Proof p) {
        ArrayList<Line> lines = new ArrayList<>();
        for (int i = 0; i < p.getNumPremises(); ++i) {
            Line l = p.getLine(i);
            if (l.getExpressionString().trim().length() > 0)
                lines.add(l);
        }
        lines.sort(Comparator.comparing(Line::getExpressionString));
        return lines;
    }

    private <T> boolean hasEqualObjects(ArrayList<T> l1, ArrayList<T> l2) {
        if (l1.size() != l2.size())
            return false;
        for (T obj : l1) {
            int i = 0;
            while (i < l2.size()) {
                if (obj.equals(l2.get(i)))
                    break;
                i++;
            }
            if (i == l2.size())
                return false;
            l2.remove(i);
        }
        return true;
    }

    private boolean checkPremises(Proof problem, Proof solution) {
        return hasEqualObjects(getPremises(problem), getPremises(solution));
    }

    private boolean checkGoals(Proof problem, Proof solution) {
        return hasEqualObjects(getGoals(problem), getGoals(solution));
    }

    private boolean checkRuleConstraints(Proof problem, Proof solution) {
        HashSet<RuleList> allowedRules = problem.getAllowedRules();
        if (allowedRules.size() == 0)
            return true;
        for (int i = 0; i < solution.getNumLines(); ++i) {
            Line l = solution.getLine(i);
            if (!l.isAssumption() && l.getSelectedRule() != null && !allowedRules.contains(l.getSelectedRule()))
                return false;
        }
        return true;
    }

    @Override
    public boolean isSolutionForProblem(@NotNull Problem<LibAris> problem, @NotNull Problem<LibAris> solution) {
        Proof prb = ((ArisProofProblem) problem).getProof();
        Proof sol = ((ArisProofProblem) solution).getProof();
        return checkGoals(prb, sol) && checkPremises(prb, sol) && checkRuleConstraints(prb, sol);
    }

    @Override
    public double gradeSolution(@NotNull Problem<LibAris> solution) {
        Proof proof = ((ArisProofProblem) solution).getProof();
        proof.verifyProof();
        ArrayList<Goal> goals = getGoals(proof);
        double correct = 0;
        for (Goal g : goals)
            if (g.getStatus() == Proof.Status.CORRECT)
                correct++;
        return goals.size() > 0 ? correct / (double) goals.size() : 0;
    }
}
