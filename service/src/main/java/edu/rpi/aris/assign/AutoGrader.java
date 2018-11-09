package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;

public interface AutoGrader<T extends ArisModule> {

    boolean isSolutionForProblem(@NotNull Problem<T> problem, @NotNull Problem<T> solution) throws Exception;

    double gradeSolution(@NotNull Problem<T> solution) throws Exception;

}
