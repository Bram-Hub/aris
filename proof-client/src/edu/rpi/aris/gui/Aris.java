package edu.rpi.aris.gui;

import edu.rpi.aris.Main;
import edu.rpi.aris.Update;
import edu.rpi.aris.UpdateProgress;
import edu.rpi.aris.proof.Proof;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

public class Aris extends Application {

    public static Aris instance = null;
    private static Logger logger = LogManager.getLogger(Aris.class);

    private MainWindow mainWindow = null;
    private File jarPath = new File(Aris.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
    private Update update = new Update(Update.Stream.CLIENT, jarPath);

    public Aris() throws URISyntaxException {
    }

    public static MainWindow showProofWindow(Stage stage, Proof p) throws IOException {
        MainWindow window = p == null ? new MainWindow(stage, EditMode.UNRESTRICTED) : new MainWindow(stage, p, EditMode.UNRESTRICTED);
        window.show();
        return window;
    }

    public static Aris getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) throws IOException {
        if (Main.doUpdate()) {
            selfUpdate();
        } else {
            instance = this;
            mainWindow = showProofWindow(stage, null);
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
        }).start();
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

    public MainWindow getMainWindow() {
        return mainWindow;
    }
}
