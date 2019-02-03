package edu.rpi.aris.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

public class GuiConfig {

    public static final String[] SYMBOL_BUTTONS = new String[]{"∧", "∨", "¬", "→", "↔", "⊥", "∀", "∃", "×", "≠", "⊆", "∈"};
    private static final Logger logger = LogManager.getLogger(GuiConfig.class);
    private static final Preferences preferences = Preferences.userNodeForPackage(GuiConfig.class);
    //Java preferences api storage constants
    private static final String OR_KEY = "OR_KEY";
    private static final String NOT_EQUALS_KEY = "NOT_EQUALS_KEY";
    private static final String NOT_KEY = "NOT_KEY";
    private static final String COND_KEY = "COND_KEY";
    private static final String BICONDITIONAL_KEY = "BICONDITIONAL_KEY";
    private static final String CONTRA_KEY = "CONTRA_KEY";
    private static final String UNIVERSAL_KEY = "UNIVERSAL_KEY";
    private static final String EXISTENTIAL_KEY = "EXISTENTIAL_KEY";
    private static final String MULTIPLICATION_KEY = "MULTIPLICATION_KEY";
    private static final String PASTE_KEY = "PASTE_KEY";
    private static final String CUT_KEY = "CUT_KEY";
    private static final String COPY_KEY = "COPY_KEY";
    private static final String REDO_KEY = "REDO_KEY";
    private static final String UNDO_KEY = "UNDO_KEY";
    private static final String SAVE_AS_KEY = "SAVE_AS_KEY";
    private static final String SAVE_KEY = "SAVE_KEY";
    private static final String OPEN_PROOF_KEY = "OPEN_PROOF_KEY";
    private static final String NEW_PROOF_KEY = "NEW_PROOF_KEY";
    private static final String VERIFY_PROOF_KEY = "VERIFY_PROOF_KEY";
    private static final String ADD_GOAL_KEY = "ADD_GOAL_KEY";
    private static final String VERIFY_LINE_KEY = "VERIFY_LINE_KEY";
    private static final String END_SUB_KEY = "END_SUB_KEY";
    private static final String START_SUB_KEY = "START_SUB_KEY";
    private static final String NEW_PREMISE_KEY = "NEW_PREMISE_KEY";
    private static final String DELETE_LINE_KEY = "DELETE_LINE_KEY";
    private static final String NEW_LINE_KEY = "NEW_LINE_KEY";
    private static final String HIDE_RULES_PANEL = "HIDE_RULES_PANEL";
    private static final String HIDE_OPERATOR_PANEL = "HIDE_OPERATOR_PANEL";
    private static final String USERNAME_KEY = "USERNAME_KEY";
    private static final String SELECTED_COURSE_ID = "SELECTED_COURSE_ID";
    private static final String LAST_SAVE_DIR = "LAST_SAVE_DIR";
    private static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String AND_KEY = "AND_KEY";

//    private static final String SERVER_ADDRESS = "SERVER_ADDRESS";
//    public static final SimpleStringProperty serverAddress = new SimpleStringProperty(preferences.get(SERVER_ADDRESS, null));
//    private static final String SERVER_PORT = "SERVER_PORT";
//    public static final SimpleIntegerProperty serverPort = new SimpleIntegerProperty(preferences.getInt(SERVER_PORT, ArisAssign.DEFAULT_PORT));

    private static final String[][] defaultKeyMap = new String[][]{{preferences.get(AND_KEY, "&"), "∧"},
            {preferences.get(OR_KEY, "|"), "∨"},
            {preferences.get(NOT_KEY, "~"), "¬"},
            {preferences.get(COND_KEY, "$"), "→"},
            {preferences.get(BICONDITIONAL_KEY, "%"), "↔"},
            {preferences.get(CONTRA_KEY, "^"), "⊥"},
            {preferences.get(UNIVERSAL_KEY, "@"), "∀"},
            {preferences.get(EXISTENTIAL_KEY, "?"), "∃"},
            {preferences.get(NOT_EQUALS_KEY, "#"), "≠"},
            {preferences.get(MULTIPLICATION_KEY, "*"), "×"}};
    private static final HashMap<String, String> keyPreferenceMap = new HashMap<>();
    public static File CLIENT_CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    private static GuiConfig configManager;

    static {
        keyPreferenceMap.put("∧", AND_KEY);
        keyPreferenceMap.put("∨", OR_KEY);
        keyPreferenceMap.put("¬", NOT_KEY);
        keyPreferenceMap.put("→", COND_KEY);
        keyPreferenceMap.put("↔", BICONDITIONAL_KEY);
        keyPreferenceMap.put("⊥", CONTRA_KEY);
        keyPreferenceMap.put("∀", UNIVERSAL_KEY);
        keyPreferenceMap.put("∃", EXISTENTIAL_KEY);
        keyPreferenceMap.put("≠", NOT_EQUALS_KEY);
        keyPreferenceMap.put("×", MULTIPLICATION_KEY);
    }

    static {
        if (!CLIENT_CONFIG_DIR.exists()) {
            if (!CLIENT_CONFIG_DIR.mkdirs())
                logger.error("Failed to create configuration directory");
            Path path = CLIENT_CONFIG_DIR.toPath();
            try {
                Object hidden = Files.getAttribute(path, "dos:hidden", LinkOption.NOFOLLOW_LINKS);
                if (hidden instanceof Boolean && !((Boolean) hidden))
                    Files.setAttribute(path, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public final SimpleBooleanProperty hideRulesPanel = new SimpleBooleanProperty(preferences.getBoolean(HIDE_RULES_PANEL, false));
    public final SimpleBooleanProperty hideOperatorsPanel = new SimpleBooleanProperty(preferences.getBoolean(HIDE_OPERATOR_PANEL, false));
    public final SimpleObjectProperty<KeyCombination> newProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_LINE_KEY, "Ctrl+A")));
    public final SimpleObjectProperty<KeyCombination> deleteProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(DELETE_LINE_KEY, "Ctrl+D")));
    public final SimpleObjectProperty<KeyCombination> newPremiseKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_PREMISE_KEY, "Ctrl+R")));
    public final SimpleObjectProperty<KeyCombination> startSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(START_SUB_KEY, "Ctrl+P")));
    public final SimpleObjectProperty<KeyCombination> endSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(END_SUB_KEY, "Ctrl+E")));
    public final SimpleObjectProperty<KeyCombination> verifyLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(VERIFY_LINE_KEY, "Ctrl+F")));
    public final SimpleObjectProperty<KeyCombination> addGoalKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(ADD_GOAL_KEY, "Ctrl+G")));
    public final SimpleObjectProperty<KeyCombination> verifyProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(VERIFY_PROOF_KEY, "Ctrl+Shift+F")));
    public final SimpleObjectProperty<KeyCombination> newProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_PROOF_KEY, "Ctrl+N")));
    public final SimpleObjectProperty<KeyCombination> openProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(OPEN_PROOF_KEY, "Ctrl+O")));
    public final SimpleObjectProperty<KeyCombination> saveProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(SAVE_KEY, "Ctrl+S")));
    public final SimpleObjectProperty<KeyCombination> saveAsProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(SAVE_AS_KEY, "Ctrl+Shift+S")));
    public final SimpleObjectProperty<KeyCombination> undoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(UNDO_KEY, "Ctrl+Z")));
    public final SimpleObjectProperty<KeyCombination> redoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(REDO_KEY, "Ctrl+Y")));
    public final SimpleObjectProperty<KeyCombination> copyKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(COPY_KEY, "Ctrl+C")));
    public final SimpleObjectProperty<KeyCombination> cutKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(CUT_KEY, "Ctrl+X")));
    public final SimpleObjectProperty<KeyCombination> pasteKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(PASTE_KEY, "Ctrl+V")));

    private final HashMap<SimpleObjectProperty<KeyCombination>, Pair<String, String>> keyComboDescriptions = new HashMap<>();

    @FXML
    private VBox aliasBox;
    @FXML
    private VBox shortcutBox;
    @FXML
    private CheckBox oprCheckBox;
    @FXML
    private CheckBox ruleCheckBox;
//    @FXML
//    private TextField serverAddressText;
//    @FXML
//    private TableView<X509Certificate> certificateTable;
//    @FXML
//    private TableColumn<X509Certificate, String> addressColumn;
//    @FXML
//    private TableColumn<X509Certificate, Label> fingerprintColumn;
//    @FXML
//    private TableColumn<X509Certificate, String> expirationColumn;
//    @FXML
//    private TableColumn<X509Certificate, Button> removeColumn;
//    @FXML
//    private Button importButton;

    private BidiMap<String, String> aliasKeyMap = new DualHashBidiMap<>();
    private HashMap<String, TextField> aliasMap = new HashMap<>();
    private HashMap<SimpleObjectProperty<KeyCombination>, Pair<KeyCombination, Button>> shortcutMap = new HashMap<>();
//    private HashSet<String> removeCerts = new HashSet<>();
    private Stage stage;
    private File saveDirectory = new File(preferences.get(LAST_SAVE_DIR, System.getProperty("user.home")));
//    private String accessToken = preferences.get(ACCESS_TOKEN, null);
    @SuppressWarnings("unchecked")
    private SimpleObjectProperty<KeyCombination>[] accelerators = new SimpleObjectProperty[]{newProofLineKey, deleteProofLineKey,
            startSubProofKey, endSubProofKey, addGoalKey, newPremiseKey, verifyLineKey, verifyProofKey, newProofKey,
            openProofKey, saveProofKey, saveAsProofKey, undoKey, redoKey, copyKey, cutKey, pasteKey};

    private GuiConfig() throws IOException {
        for (String[] s : defaultKeyMap)
            aliasKeyMap.put(s[0], s[1]);
        if (!saveDirectory.exists())
            saveDirectory = new File(System.getProperty("user.home"));

        keyComboDescriptions.put(newProofLineKey, new Pair<>("Add proof line", NEW_PROOF_KEY));
        keyComboDescriptions.put(deleteProofLineKey, new Pair<>("Delete proof line", DELETE_LINE_KEY));
        keyComboDescriptions.put(startSubProofKey, new Pair<>("Start subproof", START_SUB_KEY));
        keyComboDescriptions.put(endSubProofKey, new Pair<>("End subproof", END_SUB_KEY));
        keyComboDescriptions.put(addGoalKey, new Pair<>("Add goal", ADD_GOAL_KEY));
        keyComboDescriptions.put(newPremiseKey, new Pair<>("Add premise", NEW_PREMISE_KEY));
        keyComboDescriptions.put(verifyLineKey, new Pair<>("Verify current line", VERIFY_LINE_KEY));
        keyComboDescriptions.put(verifyProofKey, new Pair<>("Verify entire proof", VERIFY_PROOF_KEY));
        keyComboDescriptions.put(newProofKey, new Pair<>("Start a new proof", NEW_PROOF_KEY));
        keyComboDescriptions.put(openProofKey, new Pair<>("Open a saved proof", OPEN_PROOF_KEY));
        keyComboDescriptions.put(saveProofKey, new Pair<>("Save current proof", SAVE_KEY));
        keyComboDescriptions.put(saveAsProofKey, new Pair<>("Save current proof as", SAVE_AS_KEY));
        keyComboDescriptions.put(undoKey, new Pair<>("Undo", UNDO_KEY));
        keyComboDescriptions.put(redoKey, new Pair<>("Redo", REDO_KEY));
        keyComboDescriptions.put(copyKey, new Pair<>("Copy", COPY_KEY));
        keyComboDescriptions.put(cutKey, new Pair<>("Cut", CUT_KEY));
        keyComboDescriptions.put(pasteKey, new Pair<>("Paste", PASTE_KEY));

        FXMLLoader loader = new FXMLLoader(GuiConfig.class.getResource("config.fxml"));
        loader.setController(this);
        Scene scene = new Scene(loader.load(), 500, 400);
        stage = new Stage();
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setOnCloseRequest(event -> cancelConfig());
    }

    public static void setClientConfigDir(File clientConfigDir) {
        if (clientConfigDir == null)
            clientConfigDir = new File(System.getProperty("user.home"), ".aris-java");
        if (!clientConfigDir.exists())
            //noinspection ResultOfMethodCallIgnored
            clientConfigDir.mkdirs();
        CLIENT_CONFIG_DIR = clientConfigDir;
    }

    public static GuiConfig getConfigManager() {
        if (configManager == null) {
            try {
                configManager = new GuiConfig();
            } catch (IOException e) {
                logger.error("Failed to load config screen", e);
            }
        }
        return configManager;
    }

    public String replaceText(String text) {
        for (int i = 0; i < text.length(); ++i) {
            String replace;
            if ((replace = aliasKeyMap.get(String.valueOf(text.charAt(i)))) != null)
                text = text.substring(0, i) + replace + text.substring(i + 1);
        }
        return text;
    }

    @FXML
    private void initialize() {
        ArrayList<Map.Entry<String, String>> sortedAliases = new ArrayList<>(aliasKeyMap.entrySet());
        sortedAliases.sort((o1, o2) -> {
            int i1 = 0, i2 = 0;
            for (int i = 0; i < defaultKeyMap.length; ++i) {
                if (defaultKeyMap[i][1].equals(o1.getValue()))
                    i1 = i;
                if (defaultKeyMap[i][1].equals(o2.getValue()))
                    i2 = i;
            }
            return Integer.compare(i1, i2);
        });
        for (Map.Entry<String, String> alias : sortedAliases) {
            HBox box = new HBox();
            Label lbl = new Label("Key mapping for \"" + alias.getValue() + "\"");
            Separator separator = new Separator(Orientation.HORIZONTAL);
            separator.setVisible(false);
            HBox.setHgrow(separator, Priority.ALWAYS);
            TextField textField = new TextField();
            textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue)
                    Platform.runLater(textField::selectAll);
            });
            textField.textProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(textField::selectAll));
            textField.setPrefWidth(30);
            textField.setTextFormatter(new TextFormatter<String>(change -> {
                if (change.getControlNewText().length() > 1) {
                    change.setText(change.getControlNewText().substring(0, 1));
                    change.setRange(0, 1);
                }
                return change;
            }));
            aliasMap.put(alias.getValue(), textField);
            box.getChildren().addAll(lbl, separator, textField);
            aliasBox.getChildren().add(box);
        }
        for (SimpleObjectProperty<KeyCombination> prop : accelerators) {
            HBox box = new HBox();
            Label lbl = new Label(keyComboDescriptions.get(prop).getKey());
            Separator separator = new Separator(Orientation.HORIZONTAL);
            separator.setVisible(false);
            HBox.setHgrow(separator, Priority.ALWAYS);
            Button btn = new Button("Unbound");
            btn.setOnAction(actionEvent -> bind(prop));
            shortcutMap.put(prop, new Pair<>(prop.get(), btn));
            box.getChildren().addAll(lbl, separator, btn);
            shortcutBox.getChildren().add(box);
        }
//        serverAddress.addListener((observable, oldValue, newValue) -> {
//            serverAddressText.setText(serverAddress.get() + (serverPort.get() == ArisAssign.DEFAULT_PORT ? "" : ":" + serverPort.get()));
//            preferences.put(SERVER_ADDRESS, newValue);
//        });
//        serverPort.addListener((observable, oldValue, newValue) -> {
//            serverAddressText.setText(serverAddress.get() + (serverPort.get() == ArisAssign.DEFAULT_PORT ? "" : ":" + serverPort.get()));
//            preferences.putInt(SERVER_PORT, newValue.intValue());
//        });
//        addressColumn.setCellValueFactory(param -> {
//            try {
//                JcaX509CertificateHolder holder = new JcaX509CertificateHolder(param.getValue());
//                X500Name name = holder.getSubject();
//                RDN cn = name.getRDNs(BCStyle.CN)[0];
//                String dn = IETFUtils.valueToString(cn.getFirst().getValue());
//                return new SimpleStringProperty(dn);
//            } catch (CertificateEncodingException e) {
//                logger.error("Failed to get certificate dn", e);
//            }
//            return new SimpleStringProperty("Error");
//        });
//        addressColumn.setStyle("-fx-alignment: CENTER;");
//        fingerprintColumn.setCellValueFactory(param -> {
//            X509Certificate cert = param.getValue();
//            try {
//                String fingerprint = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-1", "BC").digest(cert.getEncoded())).toUpperCase();
//                fingerprint = fingerprint.replaceAll("(.{2})", ":$1").substring(1);
//                Label lbl = new Label(fingerprint);
//                lbl.setTooltip(new Tooltip(fingerprint));
//                return new SimpleObjectProperty<>(lbl);
//            } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateEncodingException e) {
//                logger.error("Failed to generate certificate fingerprint", e);
//            }
//            return new SimpleObjectProperty<>(new Label("Error"));
//        });
//        fingerprintColumn.setStyle("-fx-alignment: CENTER;");
//        expirationColumn.setCellValueFactory(param -> {
//            X509Certificate cert = param.getValue();
//            Date expiration = cert.getNotAfter();
//            if (expiration.before(new Date()))
//                return new SimpleStringProperty("Expired");
//            return new SimpleStringProperty(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(expiration));
//        });
//        expirationColumn.setStyle("-fx-alignment: CENTER;");
//        removeColumn.setCellValueFactory(param -> {
//            try {
//                JcaX509CertificateHolder holder = new JcaX509CertificateHolder(param.getValue());
//                X500Name name = holder.getSubject();
//                RDN cn = name.getRDNs(BCStyle.CN)[0];
//                String dn = IETFUtils.valueToString(cn.getFirst().getValue());
//                Button btn = new Button("Remove");
//                btn.setOnAction(actionEvent -> {
//                    removeCerts.add(dn);
//                    certificateTable.getItems().remove(param.getValue());
//                });
//                return new SimpleObjectProperty<>(btn);
//            } catch (CertificateEncodingException e) {
//                logger.error("Failed to get certificate dn", e);
//            }
//            return new SimpleObjectProperty<>(null);
//        });
//        removeColumn.setStyle("-fx-alignment: CENTER;");
//        importButton.setOnAction(actionEvent -> {
//            FileChooser chooser = new FileChooser();
//            FileChooser.ExtensionFilter pemFilter = new FileChooser.ExtensionFilter("PEM File", "*.pem");
//            FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*");
//            chooser.getExtensionFilters().setAll(pemFilter, allFilter);
//            chooser.setSelectedExtensionFilter(pemFilter);
//            chooser.setTitle("Import server certificate");
//            chooser.setInitialDirectory(saveDirectory);
//            File file = chooser.showOpenDialog(stage);
//            if (file != null && file.exists()) {
//                saveDirectory = file.getParentFile();
//                if (!Main.getClient().importSelfSignedCertificate(file)) {
//                    Alert alert = new Alert(Alert.AlertType.WARNING);
//                    alert.initOwner(stage);
//                    alert.initModality(Modality.WINDOW_MODAL);
//                    alert.setTitle("Import Failed");
//                    alert.setHeaderText("Failed to import given certificate");
//                    alert.showAndWait();
//                }
//                certificateTable.getItems().setAll(Main.getClient().getSelfSignedCertificates());
//            }
//        });
        populateConfig();
        applyConfig();
    }

    private void bind(SimpleObjectProperty<KeyCombination> prop) {
        Dialog<Pair<KeyCombination, Boolean>> dialog = new Dialog<>();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(stage);
        dialog.setTitle("Bind key");
        dialog.setHeaderText("Bind: " + keyComboDescriptions.get(prop).getKey());
        VBox vBox = new VBox();
        vBox.setSpacing(5);
        HBox hBox = new HBox();
        Label lbl = new Label(prop.get() == null ? "Unbound" : shortcutMap.get(prop).getKey().getDisplayText());
        hBox.getChildren().addAll(new Label("Current binding: "), lbl);
        vBox.getChildren().addAll(new Label("Press the key combination to bind to the shortcut or backspace to unbind"), hBox);
        dialog.getDialogPane().setContent(vBox);
        AtomicReference<KeyCombination> combo = new AtomicReference<>(prop.get());
        dialog.getDialogPane().addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.BACK_SPACE) {
                combo.set(null);
                lbl.setText("Unbound");
            } else if (!event.getCode().isModifierKey() && event.getCode() != KeyCode.UNDEFINED && (event.isAltDown() || event.isControlDown() || event.isMetaDown())) {
                KeyCombination.ModifierValue up = KeyCombination.ModifierValue.UP;
                KeyCombination.ModifierValue down = KeyCombination.ModifierValue.DOWN;
                KeyCombination newCombo = new KeyCodeCombination(event.getCode(), event.isShiftDown() ? down : up,
                        event.isControlDown() ? down : up, event.isAltDown() ? down : up, event.isMetaDown() ? down : up, up);
                combo.set(newCombo);
                lbl.setText(newCombo.getDisplayText());
            }
        });
        lbl.requestFocus();
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(param -> new Pair<>(combo.get(), param == ButtonType.OK));
        Optional<Pair<KeyCombination, Boolean>> result = dialog.showAndWait();
        if (result.isPresent() && result.get().getValue()) {
            for (Map.Entry<SimpleObjectProperty<KeyCombination>, Pair<KeyCombination, Button>> c : shortcutMap.entrySet()) {
                if (c.getKey() == prop)
                    continue;
                if (c.getValue().getKey() != null && c.getValue().getKey().equals(result.get().getKey())) {
                    configAlert(result.get().getKey().getDisplayText() + " is already bound to " + keyComboDescriptions.get(c.getKey()).getKey());
                    return;
                }
            }
            Button btn = shortcutMap.get(prop).getValue();
            btn.setText(result.get().getKey() == null ? "Unbound" : result.get().getKey().getDisplayText());
            shortcutMap.put(prop, new Pair<>(result.get().getKey(), btn));
        }
    }

    @FXML
    private void applyConfig() {
        BidiMap<String, String> newAliases = new DualHashBidiMap<>();
        for (Map.Entry<String, TextField> alias : aliasMap.entrySet()) {
            String key = alias.getKey();
            TextField tf = alias.getValue();
            String a = tf.getText();
            if (a.length() == 1) {
                if (newAliases.containsKey(a)) {
                    configAlert("The alias set for the \"" + key + "\" symbol is the same as for the \"" + newAliases.get(a) + "\" symbol");
                    return;
                }
                newAliases.put(a, key);
            }
        }
        for (Map.Entry<String, String> prefMap : keyPreferenceMap.entrySet()) {
            if (newAliases.containsValue(prefMap.getKey()))
                preferences.put(prefMap.getValue(), newAliases.getKey(prefMap.getKey()));
            else
                preferences.remove(prefMap.getValue());
        }
//        String serverInfo = serverAddressText.getText();
//        String address = serverInfo;
//        int port = ArisAssign.DEFAULT_PORT;
//        if (serverInfo.contains(":")) {
//            if (StringUtils.countMatches(serverInfo, ':') != 1) {
//                configAlert("Invalid server address: " + serverInfo);
//                return;
//            }
//            String[] split = serverInfo.split(":");
//            address = split[0];
//            try {
//                port = Integer.parseInt(split[1]);
//            } catch (NumberFormatException e) {
//                configAlert("Invalid server port: " + split[1]);
//                return;
//            }
//        }
//        try {
//            //noinspection ResultOfMethodCallIgnored
//            InetAddress.getByName(address);
//        } catch (UnknownHostException e) {
//            configAlert("Invalid server address: " + address);
//            return;
//        }
//        serverAddress.set(address);
//        preferences.put(SERVER_ADDRESS, address);
//        serverPort.set(port);
//        preferences.putInt(SERVER_PORT, port);
        for (SimpleObjectProperty<KeyCombination> prop : accelerators) {
            KeyCombination newKey = shortcutMap.get(prop).getKey();
            prop.set(newKey);
            if (newKey == null)
                preferences.remove(keyComboDescriptions.get(prop).getValue());
            else
                preferences.put(keyComboDescriptions.get(prop).getValue(), newKey.getDisplayText());
        }
        hideOperatorsPanel.set(oprCheckBox.isSelected());
        preferences.putBoolean(HIDE_OPERATOR_PANEL, oprCheckBox.isSelected());
        hideRulesPanel.set(ruleCheckBox.isSelected());
        preferences.putBoolean(HIDE_RULES_PANEL, ruleCheckBox.isSelected());
//        for (String dn : removeCerts)
//            Main.getClient().removeSelfSignedCertificate(dn);
        aliasKeyMap = newAliases;
        if (stage != null)
            stage.hide();
    }

    @FXML
    private void cancelConfig() {
        stage.hide();
        populateConfig();
    }

    private void populateConfig() {
        for (Map.Entry<String, String> alias : aliasKeyMap.entrySet())
            aliasMap.get(alias.getValue()).setText(alias.getKey());
        for (SimpleObjectProperty<KeyCombination> prop : accelerators) {
            Pair<KeyCombination, Button> shortcut = shortcutMap.get(prop);
            shortcut.getValue().setText(prop.get() == null ? "Unbound" : prop.getValue().getDisplayText());
            shortcutMap.put(prop, new Pair<>(prop.get(), shortcut.getValue()));
        }
        oprCheckBox.setSelected(hideOperatorsPanel.get());
        ruleCheckBox.setSelected(hideRulesPanel.get());
//        String serverText = serverAddress.get() == null ? "" : (serverAddress.get() + (serverPort.get() == ArisAssign.DEFAULT_PORT ? "" : ":" + serverPort.get()));
//        serverAddressText.setText(serverText);
//        certificateTable.getItems().setAll(Main.getClient().getSelfSignedCertificates());
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

    public void showConfig() {
        Platform.runLater(() -> {
            stage.show();
            stage.requestFocus();
        });
    }

    public boolean ignore(KeyEvent event) {
        for (SimpleObjectProperty a : accelerators)
            if (((KeyCombination) a.get()).match(event))
                return true;
        return false;
    }

    public File getSaveDirectory() {
        if (!saveDirectory.exists()) {
            saveDirectory = new File(System.getProperty("user.home"));
            preferences.put(LAST_SAVE_DIR, saveDirectory.getAbsolutePath());
        }
        return saveDirectory;
    }

    public void setSaveDirectory(File file) {
        if (file == null)
            saveDirectory = new File(System.getProperty("user.home"));
        else
            saveDirectory = file;
        preferences.put(LAST_SAVE_DIR, saveDirectory.getAbsolutePath());
    }

//    public synchronized String getAccessToken() {
//        return accessToken;
//    }

//    public synchronized void setAccessToken(String token) {
//        accessToken = token;
//        if (token == null)
//            preferences.remove(ACCESS_TOKEN);
//        else
//            preferences.put(ACCESS_TOKEN, token);
//    }

}
