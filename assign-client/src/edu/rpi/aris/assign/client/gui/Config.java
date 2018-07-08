package edu.rpi.aris.assign.client.gui;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ConfigProp;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.prefs.Preferences;

public class Config {

    public static final ConfigProp<Boolean> ALLOW_INSECURE = new ConfigProp<>(null, null, false);
    public static final ConfigProp<File> ADD_CERT = new ConfigProp<>(null, null, null);

    public static final File CLIENT_CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    public static final File CLIENT_MODULES_DIR = new File(CLIENT_CONFIG_DIR, "modules");
    public static final File CLIENT_MODULE_LIBS_DIR = new File(CLIENT_MODULES_DIR, "libs");
    private static final String SERVER_ADDRESS_KEY = "server_address";
    private static final String PORT_KEY = "port";
    private static final String USERNAME_KEY = "username";
    private static final String SELECTED_COURSE_ID_KEY = "selected_course";
    private static final String ACCESS_TOKEN_KEY = "access_token";
    private static final Preferences preferences = Preferences.userNodeForPackage(Config.class);

    public static final ConfigProp<String> SERVER_ADDRESS = new ConfigProp<>(preferences, SERVER_ADDRESS_KEY, preferences.get(SERVER_ADDRESS_KEY, null));
    public static final ConfigProp<String> USERNAME = new ConfigProp<>(preferences, USERNAME_KEY, preferences.get(USERNAME_KEY, null));
    public static final ConfigProp<String> ACCESS_TOKEN = new ConfigProp<>(preferences, ACCESS_TOKEN_KEY, preferences.get(ACCESS_TOKEN_KEY, null));
    public static final ConfigProp<Integer> SELECTED_COURSE_ID = new ConfigProp<>(preferences, SELECTED_COURSE_ID_KEY, preferences.getInt(SELECTED_COURSE_ID_KEY, 0));
    public static final ConfigProp<Integer> PORT = new ConfigProp<>(preferences, PORT_KEY, preferences.getInt(PORT_KEY, LibAssign.DEFAULT_PORT));

    private static final Logger logger = LogManager.getLogger(Config.class);
    private static Config instance;

    @FXML
    private TextField serverAddressText;
    @FXML
    private TableView<X509Certificate> certificateTable;
    @FXML
    private TableColumn<X509Certificate, String> addressColumn;
    @FXML
    private TableColumn<X509Certificate, Label> fingerprintColumn;
    @FXML
    private TableColumn<X509Certificate, String> expirationColumn;
    @FXML
    private TableColumn<X509Certificate, Button> removeColumn;

    private Stage stage;
    private HashSet<String> removeCerts = new HashSet<>();

    private Config() {
        stage = new Stage();
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("config.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
            return;
        }
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(AssignClient.getInstance().getMainWindow().getStage());
    }

    public static Config getInstance() {
        if (instance == null)
            instance = new Config();
        return instance;
    }

    public void show() {
        stage.show();
    }

    public void populateConfig() {
        removeCerts.clear();
        serverAddressText.setText(SERVER_ADDRESS.getValue() == null ? "" : SERVER_ADDRESS.getValue() + (PORT.getValue() == LibAssign.DEFAULT_PORT ? "" : ":" + PORT.getValue()));
        certificateTable.getItems().setAll(Client.getInstance().getSelfSignedCertificates());
    }

    private void configAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.initOwner(stage);
        alert.setTitle("Configuration Error");
        alert.setHeaderText("There is an error with the current configuration");
        alert.setContentText(message);
        alert.show();
    }

    @FXML
    public void initialize() {
        SERVER_ADDRESS.getProperty().addListener((observable, oldValue, newValue) -> serverAddressText.setText(newValue == null ? "" : newValue + (PORT.getValue() == LibAssign.DEFAULT_PORT ? "" : ":" + PORT.getValue())));
        PORT.getProperty().addListener((observable, oldValue, newValue) -> serverAddressText.setText(SERVER_ADDRESS.getValue() == null ? "" : SERVER_ADDRESS.getValue() + (newValue == LibAssign.DEFAULT_PORT ? "" : ":" + newValue)));
        addressColumn.setCellValueFactory(param -> {
            try {
                JcaX509CertificateHolder holder = new JcaX509CertificateHolder(param.getValue());
                X500Name name = holder.getSubject();
                RDN cn = name.getRDNs(BCStyle.CN)[0];
                String dn = IETFUtils.valueToString(cn.getFirst().getValue());
                return new SimpleStringProperty(dn);
            } catch (CertificateEncodingException e) {
                logger.error("Failed to get certificate dn", e);
            }
            return new SimpleStringProperty("Error");
        });
        addressColumn.setStyle("-fx-alignment: CENTER;");
        fingerprintColumn.setCellValueFactory(param -> {
            X509Certificate cert = param.getValue();
            try {
                String fingerprint = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-1", "BC").digest(cert.getEncoded())).toUpperCase();
                fingerprint = fingerprint.replaceAll("(.{2})", ":$1").substring(1);
                Label lbl = new Label(fingerprint);
                lbl.setTooltip(new Tooltip(fingerprint));
                return new SimpleObjectProperty<>(lbl);
            } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateEncodingException e) {
                logger.error("Failed to generate certificate fingerprint", e);
            }
            return new SimpleObjectProperty<>(new Label("Error"));
        });
        fingerprintColumn.setStyle("-fx-alignment: CENTER;");
        expirationColumn.setCellValueFactory(param -> {
            X509Certificate cert = param.getValue();
            Date expiration = cert.getNotAfter();
            if (expiration.before(new Date()))
                return new SimpleStringProperty("Expired");
            return new SimpleStringProperty(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(expiration));
        });
        expirationColumn.setStyle("-fx-alignment: CENTER;");
        removeColumn.setCellValueFactory(param -> {
            try {
                JcaX509CertificateHolder holder = new JcaX509CertificateHolder(param.getValue());
                X500Name name = holder.getSubject();
                RDN cn = name.getRDNs(BCStyle.CN)[0];
                String dn = IETFUtils.valueToString(cn.getFirst().getValue());
                Button btn = new Button("Remove");
                btn.setOnAction(actionEvent -> {
                    removeCerts.add(dn);
                    certificateTable.getItems().remove(param.getValue());
                });
                return new SimpleObjectProperty<>(btn);
            } catch (CertificateEncodingException e) {
                logger.error("Failed to get certificate dn", e);
            }
            return new SimpleObjectProperty<>(null);
        });
        removeColumn.setStyle("-fx-alignment: CENTER;");
        populateConfig();
    }

    @FXML
    public void importCert() {
        FileChooser chooser = new FileChooser();
        FileChooser.ExtensionFilter pemFilter = new FileChooser.ExtensionFilter("PEM File", "*.pem");
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*");
        chooser.getExtensionFilters().setAll(pemFilter, allFilter);
        chooser.setSelectedExtensionFilter(pemFilter);
        chooser.setTitle("Import server certificate");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File file = chooser.showOpenDialog(stage);
        if (file != null && file.exists()) {
            if (!Client.getInstance().importSelfSignedCertificate(file)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(stage);
                alert.initModality(Modality.WINDOW_MODAL);
                alert.setTitle("Import Failed");
                alert.setHeaderText("Failed to import given certificate");
                alert.showAndWait();
            }
            certificateTable.getItems().setAll(Client.getInstance().getSelfSignedCertificates());
        }
    }

    @FXML
    public void cancelConfig() {
        populateConfig();
        stage.hide();
    }

    @FXML
    public void applyConfig() {
        for (String dn : removeCerts)
            Client.getInstance().removeSelfSignedCertificate(dn);
        removeCerts.clear();
        String serverInfo = serverAddressText.getText();
        String address = serverInfo;
        int port = LibAssign.DEFAULT_PORT;
        if (serverInfo.contains(":")) {
            if (StringUtils.countMatches(serverInfo, ':') != 1) {
                configAlert("Invalid server address: " + serverInfo);
                return;
            }
            String[] split = serverInfo.split(":");
            address = split[0];
            try {
                port = Integer.parseInt(split[1]);
                if (port < 1 || port > 65535) {
                    configAlert("Invalid server port: " + split[1]);
                    return;
                }
            } catch (NumberFormatException e) {
                configAlert("Invalid server port: " + split[1]);
                return;
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            configAlert("Invalid server address: " + address);
            return;
        }
        SERVER_ADDRESS.setValue(address);
        PORT.setValue(port);
        stage.hide();
    }

}
