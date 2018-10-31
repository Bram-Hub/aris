package edu.rpi.aris.assign.client;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.controller.ModuleSelect;
import edu.rpi.aris.assign.client.model.LocalConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Optional;

public class AssignClient extends Application implements ArisExceptionHandler {

    private static final Logger logger = LogManager.getLogger(AssignClient.class);
    private static boolean doUpdate = false;
    private static AssignClient instance;

    private Update update;
    private ModuleSelect mainWindow;

    public AssignClient() {
        instance = this;
        LibAssign.getInstance().setArisExceptionHandler(this);
        try {
            File jarPath = new File(AssignClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
            update = new Update(Update.Stream.CLIENT, jarPath);
        } catch (URISyntaxException e) {
            logger.fatal(e);
        }
    }

    public static void main(String[] args) throws IOException {
        LibAssign.setLogLocation(new File(LocalConfig.CLIENT_STORAGE_DIR, "logs"));
        LibAssign.initModuleService(LocalConfig.CLIENT_MODULES_DIR, false);
        logger.info("Starting Assign Client with Java " + SystemUtils.JAVA_RUNTIME_VERSION + " " + SystemUtils.JAVA_VENDOR);
        logger.info("Loaded client modules: " + ModuleService.getService().moduleNames());
        LibAssign.getInstance().init(false, args, new MainCallbackListener() {
            @Override
            public void processAlreadyRunning(CommandLine cmd) {
                AssignClient.processAlreadyRunning(cmd);
            }

            @Override
            public void finishInit(CommandLine cmd) {
                AssignClient.finishInit(cmd);
            }

            @Override
            public void processIpcMessage(String msg) {
                AssignClient.processIpcMessage(msg);
            }
        });
    }

    private static void processIpcMessage(String msg) {
        switch (msg) {
            case "show-module-ui":
                if (instance != null)
                    Platform.runLater(() -> {
                        instance.mainWindow.show();
                        instance.mainWindow.getStage().requestFocus();
                    });
                break;
        }
    }

    private static void processAlreadyRunning(CommandLine cmd) {
        try {
            LibAssign.getInstance().sendIpcMessage("show-module-ui");
        } catch (IOException e) {
            logger.error("Failed to send ipc message", e);
        }
    }

    private static void finishInit(CommandLine cmd) {
        String[] split = SystemUtils.JAVA_VERSION.split("_");
        int update = split.length < 2 ? 0 : Integer.parseInt(split[1]);
        if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8) || (SystemUtils.JAVA_SPECIFICATION_VERSION.equals(JavaVersion.JAVA_1_8.toString()) && update < 40)) {
            String msg = LibAssign.NAME + " has a minimum requirement of java 1.8.0_40\nYou are running java " + SystemUtils.JAVA_VERSION + "\nPlease update java before using " + LibAssign.NAME;
            logger.log(Level.FATAL, msg);
            JOptionPane.showMessageDialog(null, msg);
            System.exit(1);
        }
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
            LocalConfig.SERVER_ADDRESS.setValue(cmd.getOptionValue('a'));
        if (port > 0)
            LocalConfig.PORT.setValue(port);
        LocalConfig.ALLOW_INSECURE.setValue(cmd.hasOption("allow-insecure"));
        if (cmd.hasOption("add-cert")) {
            String filename = cmd.getOptionValue("add-cert");
            File file = new File(filename);
            LocalConfig.ADD_CERT.setValue(file);
        }
        doUpdate = cmd.hasOption('u');
        launch(AssignClient.class, cmd.getArgs());
    }

    public static AssignClient getInstance() {
        return instance;
    }

    public ModuleSelect getMainWindow() {
        return mainWindow;
    }

    public static void displayErrorMsg(String title, String msg) {
        getInstance().getMainWindow().displayErrorMsg(title, msg);
    }

    public static void displayErrorMsg(String title, String msg, boolean wait) {
        getInstance().getMainWindow().displayErrorMsg(title, msg, wait);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Aris Assign");
        if (doUpdate) {
            selfUpdate();
        } else {
            mainWindow = new ModuleSelect(primaryStage);
            mainWindow.show();
            checkUpdate();
        }
    }

    public void checkUpdate() {
        new Thread(() -> {
            if (update.checkUpdate()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Update available");
                    alert.setHeaderText("An update is available for aris");
                    alert.setContentText("Would you like to update now?");
                    alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    Optional<ButtonType> result = alert.showAndWait();
                    result.ifPresent(bt -> {
                        if (bt == ButtonType.YES)
                            new Thread(() -> {
                                try {
                                    startUpdate();
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                    });
                });
            }
        }, "Check Update Thread").start();
    }

    public void startUpdate() throws IOException, InterruptedException {
        if (SystemUtils.IS_OS_LINUX) {
            //TODO
        } else if (SystemUtils.IS_OS_WINDOWS) {
            File java = new File(SystemUtils.JAVA_HOME);
            java = new File(java, "bin");
            java = new File(java, "javaw.exe");
            Process process = Runtime.getRuntime().exec(new String[]{"powershell.exe", "-Command", "\"Start-Process \\\"" + java.getCanonicalPath() + "\\\" \\\"-Dlog4j.log-dir=" + System.getProperty("log4j.log-dir").replaceAll(" ", "\\ ") + " -jar aris-client.jar -u\\\" -Verb RunAs -Wait\""});
            process.waitFor();
            Platform.exit();
            System.exit(52);
        } else {
            //TODO cant auto update
        }
    }

    public void selfUpdate() {
        Label description = new Label();
        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        update.setUpdateProgress(new UpdateProgress() {

            private double current = 0;
            private double total = 0;

            @Override
            public void setTotalDownloads(double total) {
                this.total = total;
                Platform.runLater(() -> {
                    if (total <= 0)
                        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    else
                        progress.setProgress(current / this.total);
                });
            }

            @Override
            public void setCurrentDownload(double current) {
                this.current = current;
                if (total > 0)
                    Platform.runLater(() -> progress.setProgress(current / total));
            }

            @Override
            public void setDescription(String desc) {
                Platform.runLater(() -> description.setText(desc));
            }
        });
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Updating");
            alert.setHeaderText("Aris is performing a self update");
            VBox box = new VBox(5);
            box.setFillWidth(true);
            box.getChildren().addAll(description, progress);
            alert.getDialogPane().setContent(box);
            alert.getButtonTypes().clear();
            alert.setOnCloseRequest(Event::consume);
            alert.show();
            new Thread(() -> {
                if (update.checkUpdate())
                    update.update();
                update.setUpdateProgress(null);
                Platform.runLater(() -> {
                    alert.hide();
                    Platform.exit();
                });
            }).start();
        });
    }

    @Override
    public void uncaughtException(Thread t, Throwable e, boolean fatal) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);

                if (mainWindow != null) {
                    alert.initModality(Modality.APPLICATION_MODAL);
                    alert.initOwner(mainWindow.getStage().getScene().getWindow());
                }

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
                pw.println(LibAssign.NAME + " " + LibAssign.VERSION);
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
    }
}
