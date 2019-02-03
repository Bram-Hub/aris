package edu.rpi.aris.gui;

import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.RuleGroup;
import edu.rpi.aris.rules.RuleList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class RuleRestrictionUI {

    private static final Logger log = LogManager.getLogger();
    private final RulesManager rulesManager;
    @FXML
    private ChoiceBox<RuleGroup> presetChoice;
    @FXML
    private ListView<RuleList> restrictedList;
    @FXML
    private ListView<RuleList> allowedList;
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
        presetChoice.getSelectionModel().select(null);
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
        presetChoice.getItems().setAll(RuleGroup.values());
        presetChoice.getSelectionModel().select(null);
        presetChoice.setConverter(new StringConverter<RuleGroup>() {
            @Override
            public String toString(RuleGroup object) {
                return object.groupName;
            }

            @Override
            public RuleGroup fromString(String string) {
                return null;
            }
        });
        presetChoice.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null)
                return;
            setAllowed(Arrays.asList(newValue.rules));
        });
    }

    @FXML
    public void remove() {
        restrictedList.getItems().addAll(allowedList.getSelectionModel().getSelectedItems());
        allowedList.getItems().removeAll(allowedList.getSelectionModel().getSelectedItems());
        Collections.sort(restrictedList.getItems());
        presetChoice.getSelectionModel().select(null);
    }

    @FXML
    public void add() {
        allowedList.getItems().addAll(restrictedList.getSelectionModel().getSelectedItems());
        restrictedList.getItems().removeAll(restrictedList.getSelectionModel().getSelectedItems());
        Collections.sort(allowedList.getItems());
        presetChoice.getSelectionModel().select(null);
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

}
