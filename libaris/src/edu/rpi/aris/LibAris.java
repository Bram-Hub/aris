package edu.rpi.aris;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class LibAris {
    public static final String NAME = "Aris";
    public static final String VERSION;
    private static final Logger logger = LogManager.getLogger(LibAris.class);

    static {
        BufferedReader reader = new BufferedReader(new InputStreamReader(LibAris.class.getResourceAsStream("VERSION")));
        String version = "UNKNOWN";
        try {
            version = reader.readLine();
            reader.close();
        } catch (IOException e) {
            logger.error("An error occurred while attempting to read the version", e);
        }
        VERSION = version;
    }

    public static void setLogLocation(File logDir) throws IOException {
        String logPath = logDir.getCanonicalPath();
        logPath += logPath.endsWith(File.separator) ? "" : File.separator;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        ConsoleAppender consoleAppender = config.getAppender("console");
        PatternLayout consolePattern = (PatternLayout) consoleAppender.getLayout();
        TimeBasedTriggeringPolicy triggeringPolicy = TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build();
        PatternLayout patternLayout = PatternLayout.newBuilder().withPattern(consolePattern.getConversionPattern()).build();
        RollingFileAppender rollingFileAppender = RollingFileAppender.newBuilder()
                .withName("fileLogger")
                .withFileName(logPath + "aris.log")
                .withFilePattern(logPath + "aris-%d{yyyy-MM-dd}.log.gz")
                .withPolicy(triggeringPolicy)
                .withLayout(patternLayout)
                .setConfiguration(config)
                .build();
        rollingFileAppender.start();
        config.addAppender(rollingFileAppender);
        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.addAppender(config.getAppender("fileLogger"), null, null);
        context.updateLoggers();
    }
}
