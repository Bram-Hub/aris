package edu.rpi.aris.gui;

import edu.rpi.aris.Update;
import edu.rpi.aris.proof.Proof;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
        instance = this;
        mainWindow = showProofWindow(stage, null);
        new Thread(() -> {
            if (checkUpdate()) {
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

    public boolean checkUpdate() {
        return update.checkUpdate();
    }

    public void startUpdate() throws IOException, InterruptedException {
        if (SystemUtils.IS_OS_LINUX) {
            //TODO
        } else if (SystemUtils.IS_OS_WINDOWS) {
            File java = new File(SystemUtils.JAVA_HOME);
            java = new File(java, "bin");
            java = new File(java, "java.exe");
            Process process = Runtime.getRuntime().exec(new String[]{"powershell.exe", "-Command", "Start-Process \"" + java.getCanonicalPath() + "\" \"-jar aris-client.jar\" -Verb RunAs -Wait"});
            process.waitFor();
            Platform.exit();
            System.exit(52);
        } else {
            //TODO cant auto update
        }
    }

    public void selfUpdate() {

    }

    public MainWindow getMainWindow() {
        return mainWindow;
    }
}
