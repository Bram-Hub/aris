package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

public interface ProblemConverter<T extends ArisModule> {

    void convertProblem(@NotNull Problem<T> problem, @NotNull OutputStream out, boolean isProblemSolution) throws Exception;

    @NotNull
    Problem<T> loadProblem(@NotNull InputStream in, boolean isProblemSolution) throws Exception;

}
