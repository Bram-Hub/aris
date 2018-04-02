package edu.rpi.aris;

import edu.rpi.aris.gui.Aris;
import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
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

public class Main implements Thread.UncaughtExceptionHandler {

    public static final Main instance = new Main();
    private static final File lockFile = new File(System.getProperty("java.io.tmpdir"), "aris_client.lock");
    private static final File ipcFile = new File(System.getProperty("java.io.tmpdir"), "aris_client.ipc");
    private static BufferedReader SYSTEM_IN = null;
    private static CommandLine cmd;
    private static Mode MODE = Mode.GUI;
    private static Client client;
    private static Logger logger = LogManager.getLogger(Main.class);
    private static FileLock lock, ipcLock;
    private static FileChannel lockFileChannel;

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

    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(instance);
        logger.info(LibAris.NAME + " version " + LibAris.VERSION);
        try {
            parseCommandLineArgs(args);
        } catch (Throwable e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        if (!tryLock()) {
            logger.info("Program already running");
            logger.info("Sending message to running program");
            //TODO
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
            if (cmd.hasOption('a'))
                GuiConfig.serverAddress.set(cmd.getOptionValue('a'));
            if (port > 0)
                GuiConfig.serverPort.set(port);
            client = new Client();
            client.setAllowInsecure(cmd.hasOption("allow-insecure"));
            if (cmd.hasOption("add-cert")) {
                String filename = cmd.getOptionValue("add-cert");
                File file = new File(filename);
                client.importSelfSignedCertificate(file);
            }
        switch (MODE) {
            case GUI:
                Aris.launch(Aris.class, args);
                break;
        }
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
                            System.out.println(line);
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

    private static boolean tryLock() {
        try {
            lockFileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = lockFileChannel.tryLock();
            if (lock == null) {
                lockFileChannel.close();
                return false;
            }
            lockFile.deleteOnExit();
        } catch (Throwable e) {
            return false;
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

    public static Mode getMode() {
        return MODE;
    }

    private static void parseCommandLineArgs(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("h", "help", false, "Displays this help screen");
        options.addOption(null, "allow-insecure", false, "Allows aris to connect to servers using self signed certificates (WARNING! Doing this is not recommended as it allows the connection to be intercepted)");
        options.addOption(null, "add-cert", true, "Adds the given X509 encoded certificate to the client's trusted certificate store");
        options.addOption("a", "server-address", true, "Sets the server address to connect to");
        options.addOption("p", "port", true, "Sets the port to connect to or the port for the server to run on (Default: 9001)");
        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar aris.jar [options]", options);
            System.exit(0);
        }
    }

    public static synchronized Client getClient() {
        return client;
    }

    private static BufferedReader getSystemIn() {
        if (SYSTEM_IN == null)
            SYSTEM_IN = new BufferedReader(new InputStreamReader(System.in));
        return SYSTEM_IN;
    }

    public static String readLine() {
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

    public static char[] readPassword() {
        if (System.console() == null)
            return readLine().toCharArray();
        else
            return System.console().readPassword();
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
                logger.fatal("The program will not exit");
                System.exit(1);
            }
        });
        bugReportThread.start();
        if (Main.MODE == Main.Mode.GUI) {
            try {
                Platform.runLater(() -> {
                    try {
                        Alert alert = new Alert(Alert.AlertType.ERROR);

                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.initOwner(Aris.getInstance().getMainWindow().getStage().getScene().getWindow());

                        alert.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
                        alert.getDialogPane().setPrefWidth(Region.USE_COMPUTED_SIZE);

                        alert.getDialogPane().setPrefWidth(600);
                        alert.getDialogPane().setPrefHeight(500);

                        alert.setTitle("Critical Error");
                        if (fatal) {
                            alert.setHeaderText("He's dead, Jim!");
                            alert.setContentText("An error has occurred and Aris was unable to recover\n" +
                                    "A bug report was generated and sent to the Aris developers");
                        } else {
                            alert.setHeaderText("99 little bugs in the code\n" +
                                    "99 little bugs\n" +
                                    "Take one down, patch it around\n" +
                                    "137 little bugs in the code");
                            alert.setContentText("An error has occurred and Aris has attempted to recover\n" +
                                    "A bug report was generated and sent to the Aris developers\n" +
                                    "It is recommended to restart the program in case Aris was unable to fully recover");
                        }

                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        pw.println("ARIS-Java " + LibAris.VERSION);
                        e.printStackTrace(pw);
                        String exceptionText = sw.toString();

                        Label label = new Label("Error details:");

                        TextArea textArea = new TextArea(exceptionText);
                        textArea.setEditable(false);
                        textArea.setWrapText(false);

                        textArea.setMaxWidth(Double.MAX_VALUE);
                        textArea.setMaxHeight(Double.MAX_VALUE);

                        GridPane.setVgrow(textArea, Priority.ALWAYS);
                        GridPane.setHgrow(textArea, Priority.ALWAYS);

                        GridPane expContent = new GridPane();
                        expContent.setMinHeight(300);
                        expContent.setMaxWidth(Double.MAX_VALUE);
                        expContent.add(label, 0, 0);
                        expContent.add(textArea, 0, 1);

                        alert.getDialogPane().setExpandableContent(expContent);
                        alert.getDialogPane().setExpanded(true);

                        alert.showAndWait();
                    } catch (Throwable e1) {
                        logger.fatal("An error has occurred while attempting to show the error dialog");
                        logger.catching(Level.FATAL, e1);
                        logger.fatal("The program will now exit");
                        System.exit(1);
                    }
                });
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

    public enum Mode {
        GUI,
        CMD
    }
}
