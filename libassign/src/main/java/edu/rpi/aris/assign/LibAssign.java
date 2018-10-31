package edu.rpi.aris.assign;

import org.apache.commons.cli.*;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class LibAssign implements Thread.UncaughtExceptionHandler {

    public static final String VERSION;
    public static final String NAME = "Aris Assign";
    public static final int DEFAULT_PORT = 9001; // IT'S OVER 9000!
    private static final File lockFileClient = new File(System.getProperty("java.io.tmpdir"), "aris_client.lock");
    private static final File ipcFileClient = new File(System.getProperty("java.io.tmpdir"), "aris_client.ipc");
    private static final File lockFileServer = new File(System.getProperty("java.io.tmpdir"), "aris_server.lock");
    private static final File ipcFileServer = new File(System.getProperty("java.io.tmpdir"), "aris_server.ipc");
    private static final Logger logger = LogManager.getLogger(LibAssign.class);
    private static final LibAssign instance = new LibAssign();
    private static File lockFile, ipcFile;
    private static BufferedReader SYSTEM_IN = null;
    private static CommandLine cmd;
    private static FileLock lock, ipcLock;
    private static FileChannel lockFileChannel;
    private static boolean update = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (SYSTEM_IN != null)
                    SYSTEM_IN.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    static {
        BufferedReader reader = new BufferedReader(new InputStreamReader(LibAssign.class.getResourceAsStream("VERSION")));
        String version = "UNKNOWN";
        try {
            version = reader.readLine();
            reader.close();
        } catch (IOException e) {
            logger.error("An error occurred while attempting to read the version", e);
        }
        VERSION = version;
    }

    private ArisExceptionHandler exceptionHandler;
    private MainCallbackListener callbacks;

    private LibAssign() {
    }

    public static LibAssign getInstance() {
        return instance;
    }

    public static void initModuleService(File moduleDirectory, boolean isServer) {
        ModuleService.getService().initializeService(moduleDirectory, isServer);
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

    public static void showExceptionError(Throwable e) {
        instance.showExceptionError(Thread.currentThread(), e, false);
    }

    public void setArisExceptionHandler(ArisExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    private void startIpcWatch() throws IOException {
        //noinspection ResultOfMethodCallIgnored
        ipcFile.createNewFile();
        ipcFile.deleteOnExit();
        FileAlterationObserver observer = new FileAlterationObserver(System.getProperty("java.io.tmpdir"));
        FileAlterationMonitor monitor = new FileAlterationMonitor(1000);
        FileAlterationListener listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onFileChange(File file) {
                if (file.getName().equals(ipcFile.getName())) {
                    try {
                        RandomAccessFile raf = new RandomAccessFile(ipcFile, "rw");
                        if (raf.length() == 0)
                            return;
                        FileChannel channel = raf.getChannel();
                        ipcLock = channel.lock();
                        String line;
                        while ((line = raf.readLine()) != null) {
                            logger.info("Received ipc message: " + line);
                            if (callbacks != null)
                                callbacks.processIpcMessage(line);
                        }
                        raf.setLength(0);
                        ipcLock.release();
                        channel.close();
                        raf.close();
                    } catch (IOException e) {
                        logger.error("Error when monitoring for file alteration", e);
                    }
                }
            }

        };
        observer.addListener(listener);
        monitor.addObserver(observer);
        monitor.setThreadFactory(runnable -> {
            Thread t = new Thread(runnable, "IPC Parse Thread");
            t.setDaemon(true);
            return t;
        });
        try {
            monitor.start();
        } catch (Exception e) {
            logger.error("Failed to start ipc file monitor", e);
        }
    }

    public void sendIpcMessage(String msg) throws IOException {
        if (!ipcFile.exists()) {
            logger.error("Failed to send ipc message since ipc file does not exist");
            return;
        }
        PrintWriter writer = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            FileOutputStream fos = new FileOutputStream(ipcFile, true);
            ipcLock = fos.getChannel().lock();
            writer = new PrintWriter(fos, true);
            writer.println(msg);
        } finally {
            ipcLock.release();
            if (writer != null)
                writer.close();
        }
    }

    private boolean tryLock() {
        try {
            lockFileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = lockFileChannel.tryLock();
            if (lock == null) {
                lockFileChannel.close();
                return false;
            } else
                lockFile.deleteOnExit();
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void unlockFile() {
        if (lock != null) {
            try {
                lock.release();
                lockFileChannel.close();
                lockFile.delete();
                ipcFile.delete();
            } catch (IOException e) {
                logger.error("Failed to unlock file", e);
            }
        }
    }

    private void parseCommandLineArgs(String[] args, boolean isServer) throws ParseException {
        Options options = new Options();
        options.addOption("h", "help", false, "Displays this help screen");

        if (isServer) {
            options.addOption(null, "ca", true, "Specifies an X509 encoded CA certificate");
            options.addOption(null, "key", true, "Specifies a private key");
            options.addOption(null, "add-user", true, "Adds the given user to the database as an instructor");
            options.addOption(null, "password", true, "Sets the password for the given user");
        } else {
            options.addOption(null, "allow-insecure", false, "Allows aris to connect to servers using self signed certificates (WARNING! Doing this is not recommended as it allows the connection to be intercepted)");
            options.addOption(null, "add-cert", true, "Adds the given X509 encoded certificate to the client's trusted certificate store");
            options.addOption("a", "server-address", true, "Sets the server address to connect to");
        }

        options.addOption("p", "port", true, "Sets the port to connect to or the port for the server to run on (Default: 9001)");
        options.addOption("u", "update", false, "Runs a self update then exits");

        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar aris.jar [options]", options);
            System.exit(0);
        }
    }

    private BufferedReader getSystemIn() {
        if (SYSTEM_IN == null)
            SYSTEM_IN = new BufferedReader(new InputStreamReader(System.in));
        return SYSTEM_IN;
    }

    public String readLine() {
        if (System.console() == null) {
            try {
                return getSystemIn().readLine();
            } catch (IOException e) {
                return "";
            }
        } else {
            return System.console().readLine();
        }
    }

    public char[] readPassword() {
        if (System.console() == null)
            return readLine().toCharArray();
        else
            return System.console().readPassword();
    }

    public boolean doUpdate() {
        return update;
    }

    public void init(boolean isServer, String[] args, MainCallbackListener callbacks) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(instance);
        this.callbacks = callbacks;
        logger.info(NAME + " " + (isServer ? "Server" : "Client") + " version " + VERSION);
        lockFile = isServer ? lockFileServer : lockFileClient;
        ipcFile = isServer ? ipcFileServer : ipcFileClient;
        try {
            parseCommandLineArgs(args, isServer);
        } catch (Throwable e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        update = cmd.hasOption("update");
        if (!update) {
            if (!tryLock()) {
                logger.warn(NAME + " already running");
                try {
                    callbacks.processAlreadyRunning(cmd);
                } catch (IOException e) {
                    logger.catching(e);
                }
                System.exit(0);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(this::unlockFile));
            startIpcWatch();
            callbacks.finishInit(cmd);
        }
    }

    public void showExceptionError(Thread t, Throwable e, boolean fatal) {
        if (fatal) {
            logger.fatal("He's dead, Jim!");
            logger.catching(Level.FATAL, e);
        } else {
            logger.error("99 little bugs in the code");
            logger.error("99 little bugs");
            logger.error("Take one down, patch it around");
            logger.error("137 little bugs in the code");
            logger.catching(e);
        }
        Thread bugReportThread = new Thread(() -> {
            try {
                generateBugReport(t, e);
            } catch (Throwable e1) {
                logger.fatal("An error has occurred while attempting to report a different error");
                logger.catching(Level.FATAL, e1);
                logger.fatal("The program will now exit");
                System.exit(1);
            }
        });
        bugReportThread.start();
        if (exceptionHandler != null) {
            try {
                exceptionHandler.uncaughtException(t, e, fatal);
            } catch (Throwable e1) {
                logger.fatal("An error has occurred while attempting to show the error dialog");
                logger.catching(Level.FATAL, e1);
                logger.fatal("The program will now exit");
                System.exit(1);
            }
        }
        try {
            bugReportThread.join();
        } catch (InterruptedException e1) {
            logger.error("Interrupted while sending bug report", e1);
        }
        if (fatal)
            System.exit(1);
    }

    private void generateBugReport(Thread t, Throwable e) {
        //TODO
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        showExceptionError(t, e, false);
    }

}
