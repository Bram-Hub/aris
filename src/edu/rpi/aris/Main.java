package edu.rpi.aris;

import edu.rpi.aris.gui.Aris;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.server.Server;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

public class Main implements Thread.UncaughtExceptionHandler {

    public static final Main instance = new Main();

    public static final BufferedReader SYSTEM_IN = new BufferedReader(new InputStreamReader(System.in));
    public static final String VERSION = "0.1";
    public static final String NAME = "Aris";
    private static CommandLine cmd;
    private static Mode MODE = Mode.CMD;
    private Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(instance);
        try {
            parseCommandLineArgs(args);
        } catch (Throwable e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        MODE = cmd.hasOption("server") ? Mode.SERVER : Mode.GUI;
        if (MODE != Mode.SERVER) {
            if (cmd.hasOption("add-cert")) {
                String filename = cmd.getOptionValue("add-cert");
                File file = new File(filename);
                Client.importSelfSignedCertificate(file);
            }
        }
        switch (MODE) {
            case GUI:
                Aris.launch(Aris.class, args);
                break;
            case SERVER:
                String ca = cmd.getOptionValue("ca");
                String key = cmd.getOptionValue("key");
                if (ca != null && key == null)
                    throw new IOException("CA certificate specified without private key");
                else if (ca == null && key != null)
                    throw new IOException("Private key specified without CA certificate");
                File caFile = ca == null ? null : new File(ca);
                File keyFile = key == null ? null : new File(key);
                System.out.println("Creating server");
                new Server(9000, caFile, keyFile).run();
                break;
        }
    }

    public static Mode getMode() {
        return MODE;
    }

    private static void parseCommandLineArgs(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("s", "server", false, "Runs aris in server mode");
        options.addOption(null, "ca", true, "Specifies an X509 encoded CA certificate for server mode");
        options.addOption(null, "key", true, "Specifies a private key for server mode");
        options.addOption("h", "help", false, "Displays this help screen");
        options.addOption(null, "allow-insecure", false, "Allows aris to connect to servers using self signed certificates (WARNING! Doing this is not recommended as it allows the connection to be intercepted)");
        options.addOption(null, "add-cert", true, "Adds the given X509 encoded certificate to the client's trusted certificate store");
        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar aris.jar [options]", options);
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
                        pw.println("ARIS-Java " + Main.VERSION);
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
        CMD,
        SERVER
    }
}
