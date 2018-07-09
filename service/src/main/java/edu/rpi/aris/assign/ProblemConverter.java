package edu.rpi.aris.assign;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ProblemConverter {

    boolean convertProblem(Problem problem, OutputStream out, boolean isProblemSolution) throws IOException, ArisModuleException;

    Problem loadProblem(InputStream in) throws IOException, ArisModuleException;

}
