package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is used to perform auto grading for this {@link ArisModule}. It is assumed that the returned
 * AutoGrader is thread safe as the server may be calling this on multiple threads to grade multiple assignments
 * concurrently
 *
 * @param <T> the {@link ArisModule} this {@link AutoGrader} is part of
 */
public interface AutoGrader<T extends ArisModule> {

    /**
     * Returns true if the given solution is based on the given problem. Note: this function should not check the
     * solution for correctness and should only check to ensure the solution is not the solution for a different problem
     *
     * @param problem  the problem the solution is expected to be based upon
     * @param solution the solution that should be a solution to the given problem
     * @return true if the solution is based on the problem
     * @throws Exception for any error that may occur while checking the solution against the problem
     * @see Problem
     * @see AutoGrader#gradeSolution(Problem)
     */
    boolean isSolutionForProblem(@NotNull Problem<T> problem, @NotNull Problem<T> solution) throws Exception;

    /**
     * Grades the given solution for correctness. The returned value should be a double between 0 and 1
     * (0.0 <= grade <= 1.0) A 1.0 being a perfect solution and a 0.0 being an incorrect solution. If your module has
     * the ability to give partial credit this should be represented as a fractional value between 0 and 1
     *
     * @param solution the solution to check for correctness
     * @return the grade (0.0 <= grade <= 1.0)
     * @throws Exception for any error that may occur while grading the solution
     * @see Problem
     */
    double gradeSolution(@NotNull Problem<T> solution) throws Exception;

}
