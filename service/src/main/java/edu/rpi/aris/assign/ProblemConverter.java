package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;

import java.io.InputStream;
import java.io.OutputStream;

public interface ProblemConverter<T extends ArisModule> {

    boolean convertProblem(Problem<T> problem, OutputStream out, boolean isProblemSolution) throws Exception;

    Problem<T> loadProblem(InputStream in, boolean isProblemSolution) throws Exception;

}
