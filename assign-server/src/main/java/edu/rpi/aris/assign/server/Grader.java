package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.NamedThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Grader {

    private static final Logger log = LogManager.getLogger();
    private static final Grader instance = new Grader(AssignServerMain.getServer().getConfig().getGradeThreads());
    private final ThreadPoolExecutor executor;

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

    public void grade(int submissionId) {
        log.info("Grading submission " + submissionId);
        try (Connection connection = AssignServerMain.getServer().getDbManager().getConnection()) {

        } catch (Throwable e) {
            log.error("An error occurred while grading the submission", e);
        }
    }

}
