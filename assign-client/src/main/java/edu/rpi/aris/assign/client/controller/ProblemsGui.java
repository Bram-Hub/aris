package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.dialog.ImportProblemsDialog;
import edu.rpi.aris.assign.client.dialog.ProblemDialog;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.LocalConfig;
import edu.rpi.aris.assign.client.model.Problems;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Pair;
import javafx.util.converter.DefaultStringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ProblemsGui implements TabGui {

    private static final Logger log = LogManager.getLogger();
    private static final ModuleUIOptions MODIFY_OPTIONS = new ModuleUIOptions(EditMode.CREATE_EDIT_PROBLEM, "Modify Problem", true, true, true, true, false);
    private static final FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter("All Files", "*");
    private final SimpleStringProperty tabName = new SimpleStringProperty("Problems");
    @FXML
    private TableView<Problems.Problem> problemTbl;
    @FXML
    private TableColumn<Problems.Problem, String> name;
    @FXML
    private TableColumn<Problems.Problem, String> module;
    @FXML
    private TableColumn<Problems.Problem, String> createdBy;
    @FXML
    private TableColumn<Problems.Problem, String> createdOn;
    @FXML
    private TableColumn<Problems.Problem, Node> modify;
    private CurrentUser userInfo = CurrentUser.getInstance();
    private Problems problems = new Problems(this);
    private Parent root;

    public ProblemsGui() {
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("/edu/rpi/aris/assign/client/view/problems_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public Parent getRoot() {
        return root;
    }

    public Problems getProblems() {
        return problems;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public String getName() {
        return tabName.get();
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return tabName;
    }

    @Override
    public boolean requiresOnline() {
        return true;
    }

    @Override
    public void load(boolean reload) {
        problems.loadProblems(reload);
    }

    @Override
    public void unload() {
        problems.clear();
    }

    @Override
    public void closed() {

    }

    @FXML
    public void initialize() {
        Label placeHolderLbl = new Label();
        problemTbl.setPlaceholder(placeHolderLbl);
        placeHolderLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (!userInfo.isLoggedIn())
                return "Not Logged In";
            else if (problems.isLoadError())
                return "Error Loading Problems";
            else
                return "No Problems Added";
        }, userInfo.loginProperty(), userInfo.loadingProperty(), problems.loadErrorProperty()));
        problemTbl.setItems(problems.getProblems());
        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                problems.clear();
        });
        name.setCellValueFactory(param -> param.getValue().nameProperty());
        module.setCellValueFactory(param -> param.getValue().moduleProperty());
        createdBy.setCellValueFactory(param -> param.getValue().createdByProperty());
        createdOn.setCellValueFactory(param -> param.getValue().createdOnProperty());
        modify.setCellValueFactory(param -> param.getValue().modifyButtonProperty());
        name.setOnEditCommit(event -> {
            if (event.getNewValue().equals(event.getOldValue()))
                return;
            event.getRowValue().nameProperty().set(event.getNewValue());
            problems.renamed(event.getRowValue());
        });
        name.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
    }

    @FXML
    public <T extends ArisModule> void createProblem() {
        try {
            ProblemDialog<T> dialog = new ProblemDialog<>(AssignGui.getInstance().getStage());
            Optional<Triple<String, String, Problem<T>>> result = dialog.showAndWait();
            result.ifPresent(triple -> problems.createProblem(triple.getLeft(), triple.getMiddle(), triple.getRight()));
        } catch (IOException e) {
            LibAssign.showExceptionError(e);
        }
    }

    @FXML
    public void importProblem() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Import Problem Files");
        dialog.setHeaderText("Import Problem Files");
        VBox box = new VBox(5);
        box.getChildren().add(new Label("Select Module:"));
        ComboBox<String> modules = new ComboBox<>();
        modules.setMaxWidth(Double.MAX_VALUE);
        modules.getItems().addAll(ModuleService.getService().moduleNames());
        Collections.sort(modules.getItems());
        if (modules.getItems().size() == 1)
            modules.getSelectionModel().select(0);
        box.getChildren().add(modules);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(modules.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(param -> param == ButtonType.OK ? modules.getSelectionModel().getSelectedItem() : null);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::importModuleProblems);
    }

    public boolean confirmDelete(String name) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Delete Problem");
        alert.setHeaderText("Are you sure you want to delete \"" + name + "\"?");
        alert.setContentText("The problem and ANY ASSOCIATED SUBMISSIONS will be DELETED from any assignments. THIS CANNOT BE UNDONE!");
        alert.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.initOwner(AssignGui.getInstance().getStage());
        alert.initModality(Modality.APPLICATION_MODAL);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public <T extends ArisModule> void modifyProblem(Problems.Problem problemInfo, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        if (clientModule == null) {
            AssignClient.displayErrorMsg("Client GUI Missing", "No client GUI for module \"" + module.getModuleName() + "\"");
            return;
        }
        ModuleUI<T> moduleUI = clientModule.createModuleGui(MODIFY_OPTIONS, problem);
        moduleUI.setDescription("Modify Problem \"" + problemInfo.getName() + "\"");
        moduleUI.setModuleUIListener(new ModuleUIAdapter() {

            @Override
            public boolean guiCloseRequest(boolean hasUnsavedChanges) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Upload Problem?");
                alert.setHeaderText("Would you like to upload the problem to the server?");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                Window window = moduleUI.getUIWindow();
                if (window != null) {
                    alert.initOwner(window);
                    alert.initModality(Modality.WINDOW_MODAL);
                } else {
                    alert.initOwner(AssignGui.getInstance().getStage());
                    alert.initModality(Modality.APPLICATION_MODAL);
                }
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    uploadProblem();
                } else if (hasUnsavedChanges) {
                    alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("You have unsaved changes that will be lost. Are you sure you want to exit?");
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    if (window != null) {
                        alert.initOwner(window);
                        alert.initModality(Modality.WINDOW_MODAL);
                    } else {
                        alert.initOwner(AssignGui.getInstance().getStage());
                        alert.initModality(Modality.APPLICATION_MODAL);
                    }
                    result = alert.showAndWait();
                    return result.isPresent() && result.get() == ButtonType.YES;
                }
                return true;
            }

            @Override
            public boolean saveProblemLocally() {
                return problems.saveLocalModification(problemInfo, problem, module);
            }

            @Override
            public void uploadProblem() {
                problems.uploadModifiedProblem(problemInfo, problem);
                try {
                    moduleUI.hide();
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            }
        });
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

    private <T extends ArisModule> void importModuleProblems(String moduleName) {
        System.out.println("Import " + moduleName);
        ArisModule<T> module = ModuleService.getService().getModule(moduleName);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import " + moduleName + " files");
        try {
            List<String> exts = module.getProblemFileExtensions();
            boolean first = true;
            for (String ext : exts) {
                FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter(moduleName + " Problem File (." + ext + ")", "*." + ext);
                chooser.getExtensionFilters().add(extensionFilter);
                if (first) {
                    chooser.setSelectedExtensionFilter(extensionFilter);
                    first = false;
                }
            }
            chooser.getExtensionFilters().add(allFiles);
            chooser.setInitialDirectory(new File(LocalConfig.LAST_FILE_LOC.getValue()));
            List<File> importList = chooser.showOpenMultipleDialog(AssignGui.getInstance().getStage());
            importFiles(importList, module);
        } catch (Exception e) {
            LibAssign.showExceptionError(e);
        }
    }

    private <T extends ArisModule> void importFiles(List<File> files, ArisModule<T> module) {
        if (files == null || files.size() == 0)
            return;
        try {
            ProblemConverter<T> problemConverter = module.getProblemConverter();
            HashMap<File, Problem<T>> problems = new HashMap<>();
            HashSet<File> errors = new HashSet<>();
            for (File f : files) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    problems.put(f, problemConverter.loadProblem(fis, false));
                } catch (Exception e) {
                    log.error("Failed to load problem from file: " + f.getAbsolutePath(), e);
                    errors.add(f);
                }
            }
            if (errors.size() > 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Failed to load files");
                alert.setHeaderText("Some of the selected files failed to load");
                alert.setContentText("Files: " + StringUtils.join(errors.stream().map(File::getName).collect(Collectors.toList()), ", "));
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.initModality(Modality.WINDOW_MODAL);
                alert.initOwner(AssignGui.getInstance().getStage());
                alert.showAndWait();
            }
            if (problems.size() == 1) {
                File file = problems.keySet().iterator().next();
                Problem<T> problem = problems.get(file);
                String name = file.getName();
                if (name.contains("."))
                    name = name.substring(0, name.lastIndexOf('.'));
                ProblemDialog<T> dialog = new ProblemDialog<>(AssignGui.getInstance().getStage(), module.getModuleName(), name, problem);
                Optional<Triple<String, String, Problem<T>>> result = dialog.showAndWait();
                result.ifPresent(triple -> this.problems.createProblem(triple.getLeft(), triple.getMiddle(), triple.getRight()));
            } else if (problems.size() > 0) {
                ImportProblemsDialog<T> dialog = new ImportProblemsDialog<>(AssignGui.getInstance().getStage(), module.getModuleName(), problems);
                Optional<List<Pair<String, Problem<T>>>> result = dialog.showAndWait();
                result.ifPresent(list -> list.forEach(pair -> this.problems.createProblem(pair.getKey(), module.getModuleName(), pair.getValue())));
            }
        } catch (Exception e) {
            LibAssign.showExceptionError(e);
        }
    }
}
