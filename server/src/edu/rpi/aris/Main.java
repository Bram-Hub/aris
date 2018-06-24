package edu.rpi.aris;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.server.Server;
import edu.rpi.aris.net.server.ServerConfig;
import org.apache.commons.cli.*;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.SQLException;

public class Main implements Thread.UncaughtExceptionHandler {

    public static final Main instance = new Main();
    private static final File lockFile = new File(System.getProperty("java.io.tmpdir"), "aris_server.lock");
    private static final File ipcFile = new File(System.getProperty("java.io.tmpdir"), "aris_server.ipc");
    private static CommandLine cmd;
    private static Server server;
    private static Logger logger = LogManager.getLogger(Main.class);
    private static FileLock lock, ipcLock;
    private static FileChannel lockFileChannel;

    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(instance);
        ServerConfig.getInstance();
        logger.info(LibAris.NAME + " version " + LibAris.VERSION);
        try {
            parseCommandLineArgs(args);
        } catch (Throwable e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        if (ipcFile.exists() || !tryLock()) {
            logger.info("Program already running");
            logger.info("Sending message to running program");
            if (cmd.hasOption("add-user") && cmd.hasOption("password")) {
                sendIpcMessage("add-user " + cmd.getOptionValue("add-user") + " " + cmd.getOptionValue("password"));
            }
            if (cmd.hasOption('u')) {
                sendIpcMessage("update");
            }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(Main::unlockFile));
        startIpcWatch();
        int port = -1;
        if (cmd.hasOption('p')) {
            String portStr = cmd.getOptionValue('p');
            boolean error = false;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                error = true;
            }
            if (error || port <= 0 || port > 65535) {
                logger.error("Invalid port specified: " + portStr);
                System.exit(1);
            }
        }
        String ca = cmd.getOptionValue("ca");
        String key = cmd.getOptionValue("key");
        if (ca != null && key == null)
            throw new IOException("CA certificate specified without private key");
        else if (ca == null && key != null)
            throw new IOException("Private key specified without CA certificate");
        File caFile = ca == null ? null : new File(ca);
        File keyFile = key == null ? null : new File(key);
        server = new Server(port > 0 ? port : NetUtil.DEFAULT_PORT, caFile, keyFile);
        if (cmd.hasOption('u')) {
            if (!server.checkUpdate())
                System.exit(1);
        } else
            new Thread(server).start();
    }

    private static void startIpcWatch() throws IOException {
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
                            //TODO
                            String[] args = line.split(" ");
                            if (args.length == 3 && args[0].equals("add-user")) {
                                try {
                                    server.addUser(args[1], args[2], NetUtil.USER_INSTRUCTOR);
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (args.length == 1 && args[0].equals("update"))
                                server.checkUpdate();
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
            Thread t = new Thread(runnable);
            t.setDaemon(true);
            return t;
        });
        try {
            monitor.start();
        } catch (Exception e) {
            logger.error("Failed to start ipc file monitor", e);
        }
    }

    private static void sendIpcMessage(String msg) throws IOException {
        PrintWriter writer = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            FileOutputStream fos = new FileOutputStream(ipcFile, true);
            ipcLock = fos.getChannel().lock();
            writer = new PrintWriter(fos, true);
            writer.println(msg);
        } finally {
            if (ipcLock != null)
                ipcLock.release();
            if (writer != null)
                writer.close();
        }
    }

    private static boolean tryLock() {
        try {
            lockFileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = lockFileChannel.tryLock();
            if (lock == null) {
                lockFileChannel.close();
                return false;
            }
        } catch (Throwable e) {
            return false;
        } finally {
            lockFile.deleteOnExit();
        }
        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void unlockFile() {
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

    private static void parseCommandLineArgs(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption(null, "ca", true, "Specifies an X509 encoded CA certificate");
        options.addOption(null, "key", true, "Specifies a private key");
        options.addOption("h", "help", false, "Displays this help screen");
        options.addOption(null, "add-user", true, "Adds the given user to the database as an instructor");
        options.addOption(null, "password", true, "Sets the password for the given user");
        options.addOption("p", "port", true, "Sets the port to connect to or the port for the server to run on (Default: 9001)");
        options.addOption("u", "update", false, "Runs an automatic update");
        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar aris-server.jar [options]", options);
            System.exit(0);
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
        try {
            generateBugReport(t, e);
        } catch (Throwable e1) {
            logger.fatal("An error has occurred while attempting to report a different error");
            logger.catching(Level.FATAL, e1);
            logger.fatal("The program will now exit");
            System.exit(1);
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
