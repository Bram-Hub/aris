package edu.rpi.aris.gui;

import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.CustomRuleGroup;
import edu.rpi.aris.rules.RuleGroup;
import edu.rpi.aris.rules.RuleGroupPresets;
import edu.rpi.aris.rules.RuleList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class RuleRestrictionUI {

    private static final ArrayList<CustomRuleGroup> customGroups = new ArrayList<>();
    private static final Logger log = LogManager.getLogger();

    static {
        synchronized (RuleRestrictionUI.class) {
            GuiConfig.getConfigManager().loadCustomRuleGroups(customGroups);
        }
    }

    private final RulesManager rulesManager;
    @FXML
    private ChoiceBox<RuleGroup> ruleGroupChoice;
    @FXML
    private ListView<RuleList> restrictedList;
    @FXML
    private ListView<RuleList> allowedList;
    @FXML
    private Button btnDelete;
    private Stage stage;
    private Proof proof;

    public RuleRestrictionUI(Window parent, RulesManager rulesManager) {
        this.rulesManager = rulesManager;
        FXMLLoader loader = new FXMLLoader(RuleRestrictionUI.class.getResource("rule_restrict.fxml"));
        loader.setController(this);
        try {
            Parent root = loader.load();
            stage = new Stage();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(parent);
        } catch (IOException e) {
            log.error("Failed to load rule_restrict.fxml", e);
        }
    }

    public void show(Proof proof) {
        if (stage == null)
            return;
        this.proof = proof;
        setAllowed(proof.getAllowedRules());
        ruleGroupChoice.getSelectionModel().select(null);
        stage.show();
    }

    private void setAllowed(Collection<RuleList> allowed) {
        restrictedList.getItems().clear();
        allowedList.getItems().clear();
        ArrayList<RuleList> restricted = new ArrayList<>(Arrays.asList(RuleList.values()));
        ArrayList<RuleList> allowedList = new ArrayList<>(allowed);
        if (allowedList.size() == 0) {
            allowedList = restricted;
            restricted = new ArrayList<>();
        }
        restricted.removeAll(allowedList);
        Collections.sort(restricted);
        Collections.sort(allowedList);
        restrictedList.getItems().addAll(restricted);
        this.allowedList.getItems().addAll(allowedList);
    }

    @FXML
    public void initialize() {
        Callback<ListView<RuleList>, ListCell<RuleList>> callback = param -> new ListCell<RuleList>() {
            @Override
            protected void updateItem(RuleList item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.name == null) {
                    setText(null);
                } else {
                    setText(item.name);
                }
            }
        };
        restrictedList.setCellFactory(callback);
        allowedList.setCellFactory(callback);
        allowedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        restrictedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ruleGroupChoice.getItems().setAll(RuleGroupPresets.values());
        ruleGroupChoice.getItems().addAll(customGroups);
        ruleGroupChoice.getSelectionModel().select(null);
        ruleGroupChoice.setConverter(new StringConverter<RuleGroup>() {
            @Override
            public String toString(RuleGroup object) {
                return object.getName() + (object instanceof RuleGroupPresets ? " (Preset)" : "");
            }

            @Override
            public RuleGroup fromString(String string) {
                return null;
            }
        });
        ruleGroupChoice.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                btnDelete.setDisable(true);
                return;
            }
            setAllowed(Arrays.asList(newValue.getRules()));
            btnDelete.setDisable(newValue instanceof RuleGroupPresets);
        });
        btnDelete.setDisable(true);
    }

    @FXML
    public void remove() {
        restrictedList.getItems().addAll(allowedList.getSelectionModel().getSelectedItems());
        allowedList.getItems().removeAll(allowedList.getSelectionModel().getSelectedItems());
        Collections.sort(restrictedList.getItems());
        ruleGroupChoice.getSelectionModel().select(null);
    }

    @FXML
    public void add() {
        allowedList.getItems().addAll(restrictedList.getSelectionModel().getSelectedItems());
        restrictedList.getItems().removeAll(restrictedList.getSelectionModel().getSelectedItems());
        Collections.sort(allowedList.getItems());
        ruleGroupChoice.getSelectionModel().select(null);
    }

    @FXML
    public void cancel() {
        stage.hide();
    }

    @FXML
    public void apply() {
        proof.getAllowedRules().clear();
        proof.getAllowedRules().addAll(allowedList.getItems());
        proof.resetProofStatus();
        proof.modify();
        proof.verifyProof();
        rulesManager.setAvailableRules(proof.getAllowedRules());
        stage.hide();
    }

    @FXML
    public void saveCustomGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Create Custom Rule Group");
        dialog.setContentText("Rule Group Name");
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(stage);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(r -> {
            if (r.length() > 0) {
                synchronized (RuleRestrictionUI.class) {
                    CustomRuleGroup remove = null;
                    for (CustomRuleGroup c : customGroups) {
                        if (c.getName().equals(r)) {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setHeaderText("Custom Rule Group Exists");
                            alert.setContentText("A custom rule group name \"" + r + "\" already exists.\nWould you like to replace it?");
                            alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                            Optional<ButtonType> btnResult = alert.showAndWait();
                            if (!btnResult.isPresent() || btnResult.get() != ButtonType.OK)
                                return;
                            else
                                remove = c;
                        }
                    }
                    if (remove != null) {
                        customGroups.remove(remove);
                        ruleGroupChoice.getSelectionModel().select(null);
                        ruleGroupChoice.getItems().remove(remove);
                    }
                    RuleList[] rules = allowedList.getItems().toArray(new RuleList[0]);
                    CustomRuleGroup group = new CustomRuleGroup(r, rules);
                    customGroups.add(group);
                    ruleGroupChoice.getItems().add(group);
                    ruleGroupChoice.getSelectionModel().select(group);
                    GuiConfig.getConfigManager().saveCustomRuleGroups(customGroups);
                }
            }
        });
    }

    @FXML
    public void deleteCustomGroup() {
        RuleGroup g = ruleGroupChoice.getValue();
        if (!(g instanceof CustomRuleGroup))
            return;
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText("Delete custom rule group?");
        alert.setContentText("Are you sure you want to delete the \"" + g.getName() + "\" rule group?");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Optional<ButtonType> btnResult = alert.showAndWait();
        if (!btnResult.isPresent() || btnResult.get() != ButtonType.OK)
            return;
        synchronized (GuiConfig.getConfigManager()) {
            customGroups.remove(g);
            ruleGroupChoice.getSelectionModel().select(null);
            ruleGroupChoice.getItems().remove(g);
            GuiConfig.getConfigManager().saveCustomRuleGroups(customGroups);
        }
    }

}
