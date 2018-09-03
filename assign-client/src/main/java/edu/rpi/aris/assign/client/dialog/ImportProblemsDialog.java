package edu.rpi.aris.assign.client.dialog;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Pair;
import javafx.util.converter.DefaultStringConverter;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportProblemsDialog<T extends ArisModule> extends Dialog<List<Pair<String, Problem<T>>>> {

    private static final Logger log = LogManager.getLogger();

    private final String moduleName;
    private final Map<File, Problem<T>> files;
    private final HashMap<String, Pair<String, Problem<T>>> results = new HashMap<>();
    private final HashMap<String, ModuleUI<T>> guis = new HashMap<>();
    private final ArisClientModule<T> client;
    @FXML
    private Label moduleLbl;
    @FXML
    private TableView<Triple<String, SimpleStringProperty, Problem<T>>> table;
    @FXML
    private TableColumn<Triple<String, SimpleStringProperty, Problem<T>>, String> filenameColumn;
    @FXML
    private TableColumn<Triple<String, SimpleStringProperty, Problem<T>>, String> problemNameColumn;
    @FXML
    private TableColumn<Triple<String, SimpleStringProperty, Problem<T>>, Button> openColumn;

    private Button okBtn;

    public ImportProblemsDialog(Window parent, String moduleName, Map<File, Problem<T>> files) throws IOException {
        this.moduleName = moduleName;
        this.files = files;
        client = ModuleService.getService().getClientModule(moduleName);
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(ProblemDialog.class.getResource("/edu/rpi/aris/assign/client/view/import_problems_dialog.fxml"));
        loader.setController(this);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        setTitle("Import Problems");
        setHeaderText("Import Problems");
        getDialogPane().setContent(loader.load());
        setResultConverter(param -> param == ButtonType.OK ? new ArrayList<>(results.values()) : null);
    }

    @FXML
    public void initialize() {
        moduleLbl.setText(moduleName);
        problemNameColumn.setOnEditCommit(event -> {
            results.put(event.getRowValue().getLeft(), new Pair<>(event.getNewValue(), event.getRowValue().getRight()));
            event.getRowValue().getMiddle().set(event.getNewValue());
            okBtn.setDisable(false);
        });
        problemNameColumn.setOnEditStart(event -> okBtn.setDisable(true));
        problemNameColumn.setOnEditCancel(event -> okBtn.setDisable(false));
        filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLeft()));
        problemNameColumn.setCellValueFactory(param -> param.getValue().getMiddle());
        problemNameColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        openColumn.setCellValueFactory(param -> {
            Button btn = new Button("Open");
            btn.setOnAction(event -> openProblem(param.getValue().getLeft(), param.getValue().getRight()));
            return new SimpleObjectProperty<>(btn);
        });
        filenameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        problemNameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        openColumn.setStyle("-fx-alignment: CENTER;");
        for (Map.Entry<File, Problem<T>> pair : files.entrySet()) {
            try {
                String filename = pair.getKey().getName();
                String name = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
                results.put(filename, new Pair<>(name, pair.getValue()));
                table.getItems().add(new ImmutableTriple<>(filename, new SimpleStringProperty(name), pair.getValue()));
            } catch (Exception e) {
                log.error("Failed to load file: " + pair.getKey().getAbsolutePath(), e);
            }
        }
    }

    private void openProblem(String filename, Problem<T> problem) {
        ModuleUI<T> gui = guis.computeIfAbsent(filename, fn -> {
            try {
                ModuleUI<T> ui = client.createModuleGui(ProblemDialog.UI_OPTIONS, problem);
                ui.setModuleUIListener(new ModuleUIAdapter() {
                    @Override
                    public void uploadProblem() {
                        try {
                            ui.hide();
                        } catch (Exception e) {
                            LibAssign.showExceptionError(e);
                        }
                    }
                });
                ui.setModal(Modality.WINDOW_MODAL, getOwner());
                return ui;
            } catch (Exception e) {
                LibAssign.showExceptionError(e);
            }
            return null;
        });
        if (gui != null) {
            try {
                gui.show();
            } catch (Exception e) {
                LibAssign.showExceptionError(e);
            }
        }
    }

}
