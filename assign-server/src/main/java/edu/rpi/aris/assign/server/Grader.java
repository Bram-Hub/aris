package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Grader {

    private static final Logger log = LogManager.getLogger();
    private static final Grader instance = new Grader(AssignServerMain.getServer().getConfig().getGradeThreads());
    private final ThreadPoolExecutor executor;
    private final HashMap<String, AutoGrader> autoGraders = new HashMap<>();

    private Grader(int threads) {
        if (threads <= 0)
            throw new IllegalArgumentException("Threads must be greater than 0");
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads, new NamedThreadFactory("Grading thread", true));
    }

    public static Grader getInstance() {
        return instance;
    }

    public void addToGradeQueue(int submissionId) {
        log.info("Adding submission " + submissionId + " to grade queue");
        executor.submit(() -> grade(submissionId));
    }

    private void grade(int submissionId) {
        try (Connection connection = AssignServerMain.getServer().getDbManager().getConnection()) {
            try {
                connection.setAutoCommit(false);
                grade(connection, submissionId);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                log.error("An error occurred while grading the submission", e);
            }
        } catch (SQLException e) {
            log.error("An error occurred while trying to obtain a database connection", e);
        }
    }

    private <T extends ArisModule> Triple<Problem<T>, Problem<T>, ArisModule<T>> getProblems(Connection connection, int submissionId) throws Exception {
        try (PreparedStatement subStmt = connection.prepareStatement("SELECT problem_id, data FROM submission WHERE id=?;");
             PreparedStatement probStmt = connection.prepareStatement("SELECT module_name, data FROM problem WHERE id=?;")) {
            subStmt.setInt(1, submissionId);
            try (ResultSet sub = subStmt.executeQuery()) {
                if (!sub.next()) {
                    log.error("Failed to grade submission " + submissionId + ": submission does not exist");
                    return null;
                }
                int pid = sub.getInt(1);
                probStmt.setInt(1, pid);
                try (ResultSet prob = probStmt.executeQuery()) {
                    if (!prob.next()) {
                        log.error("Failed to get problem " + pid + ": problem does not exist. (How did this happen?)");
                        return null;
                    }
                    String moduleName = prob.getString(1);
                    ArisModule<T> module = ModuleService.getService().getModule(moduleName);
                    if (module == null) {
                        log.error("Missing aris module \"" + moduleName + "\" Cannot grade submission");
                        return null;
                    }
                    ProblemConverter<T> converter = module.getProblemConverter();
                    Problem<T> problem = converter.loadProblem(prob.getBinaryStream(2), false);
                    Problem<T> solution = converter.loadProblem(sub.getBinaryStream(2), true);
                    return new ImmutableTriple<>(problem, solution, module);
                }
            }
        }
    }

    private <T extends ArisModule> void grade(Connection connection, int submissionId) throws Exception {
        log.info("Grading submission " + submissionId);
        double grade;
        GradingStatus status;
        String statusStr;
        try {
            Triple<Problem<T>, Problem<T>, ArisModule<T>> problems = getProblems(connection, submissionId);
            if (problems == null)
                throw new ArisException("An error occurred loading the problem from the database. Check the logs for more info");
            Problem<T> problem = problems.getLeft();
            Problem<T> solution = problems.getMiddle();
            ArisModule<T> module = problems.getRight();
            ArisServerModule<T> server = module.getServerModule();
            if (server == null)
                throw new Exception(module.getModuleName() + " is missing the server module");
            AutoGrader<T> grader = server.getAutoGrader();
            if (grader.isSolutionForProblem(problem, solution)) {
                grade = grader.gradeSolution(solution);
                if (grade < 0)
                    grade = 0;
                if (grade > 1)
                    grade = 1;
                if (grade == 1) {
                    status = GradingStatus.CORRECT;
                    statusStr = "Correct!";
                } else if (grade == 0) {
                    status = GradingStatus.INCORRECT;
                    statusStr = "Incorrect. Please try again";
                } else {
                    status = GradingStatus.PARTIAL;
                    statusStr = "Your solution is partially correct. Keep trying to get full credit";
                }
            } else {
                status = GradingStatus.INCORRECT;
                statusStr = "The solution does not match the assigned problem";
                grade = 0;
            }
        } catch (Exception e) {
            status = GradingStatus.ERROR;
            statusStr = e.getMessage();
            grade = 0;
        }
        try (PreparedStatement updateGrade = connection.prepareStatement("UPDATE submission SET grade=?, short_status=?, status=? WHERE id=?;")) {
            updateGrade.setDouble(1, grade);
            updateGrade.setString(2, status.name());
            updateGrade.setString(3, statusStr);
            updateGrade.setInt(4, submissionId);
            updateGrade.executeUpdate();
        }
        log.info("Graded submission " + submissionId + " Grade: " + grade + " " + status.name());
    }

}
