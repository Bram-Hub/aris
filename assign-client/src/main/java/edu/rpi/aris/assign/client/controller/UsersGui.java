package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.dialog.CreateUserDialog;
import edu.rpi.aris.assign.client.dialog.PasswordResetDialog;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.ServerConfig;
import edu.rpi.aris.assign.client.model.Users;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class UsersGui implements TabGui {

    private final FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV File (*.csv)", "*.csv");
    private final FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*");

    private final CurrentUser userInfo = CurrentUser.getInstance();
    @FXML
    private TableView<Users.UserInfo> userTable;
    @FXML
    private TableColumn<Users.UserInfo, String> username;
    @FXML
    private TableColumn<Users.UserInfo, String> fullName;
    @FXML
    private TableColumn<Users.UserInfo, ServerRole> defaultRole;
    @FXML
    private TableColumn<Users.UserInfo, Button> resetPassword;
    @FXML
    private TableColumn<Users.UserInfo, Button> deleteUser;
    private Users users = new Users();
    private Parent root;

    public UsersGui() {
        FXMLLoader loader = new FXMLLoader(AssignmentsGui.class.getResource("/edu/rpi/aris/assign/client/view/users_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @Override
    public void load(boolean reload) {
        users.loadUsers(reload);
    }

    @Override
    public void unload() {
        users.clear();
    }

    @Override
    public void closed() {

    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public String getName() {
        return "Users";
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return new SimpleStringProperty(getName());
    }

    @FXML
    public void initialize() {
        Label placeHolderLbl = new Label();
        userTable.setPlaceholder(placeHolderLbl);
        placeHolderLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (!userInfo.isLoggedIn())
                return "Not Logged In";
            else if (users.isLoadError())
                return "Error Loading Users";
            else
                return "No Users... How did you get here?";
        }, userInfo.loginProperty(), userInfo.loadingProperty(), users.loadErrorProperty()));
        userTable.setItems(users.getUsers());
        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                users.clear();
        });
        userTable.editableProperty().bind(Bindings.createBooleanBinding(() -> ServerConfig.getPermissions() != null && ServerConfig.getPermissions().hasPermission(userInfo.getDefaultRole(), Perm.USER_EDIT), userInfo.defaultRoleProperty()));
        username.setCellValueFactory(param -> param.getValue().usernameProperty());
        fullName.setCellValueFactory(param -> param.getValue().fullNameProperty());
        fullName.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        fullName.setOnEditCommit(event -> {
            String old = event.getOldValue();
            event.getRowValue().fullNameProperty().set(event.getNewValue());
            users.fullNameChanged(event.getRowValue(), old, event.getNewValue());
        });
        defaultRole.setCellValueFactory(param -> param.getValue().defaultRoleProperty());
        defaultRole.setCellFactory(param -> new ComboBoxTableCell<>(ServerConfig.getRoleStringConverter(), ServerConfig.getPermissions().getRoles().toArray(new ServerRole[0])));
        defaultRole.setOnEditCommit(event -> {
            ServerRole old = event.getOldValue();
            event.getRowValue().defaultRoleProperty().set(event.getNewValue());
            users.roleChanged(event.getRowValue(), old, event.getNewValue());
        });
        resetPassword.setCellValueFactory(param -> {
            if (param.getValue().getDefaultRole().getRollRank() >= userInfo.getDefaultRole().getRollRank()) {
                Button btn = new Button("Reset Password");
                btn.setOnAction(e -> resetPassword(param.getValue()));
                return new SimpleObjectProperty<>(btn);
            } else
                return new SimpleObjectProperty<>(null);
        });
        deleteUser.setCellValueFactory(param -> {
            if (param.getValue().getUid() != userInfo.getUser().uid && param.getValue().getDefaultRole().getRollRank() >= userInfo.getDefaultRole().getRollRank()) {
                Button btn = new Button("Delete User");
                btn.setOnAction(e -> deleteUser(param.getValue()));
                return new SimpleObjectProperty<>(btn);
            } else
                return new SimpleObjectProperty<>(null);
        });
    }

    private void resetPassword(Users.UserInfo info) {
        PasswordResetDialog dialog;
        if (info.getUid() == userInfo.getUser().uid)
            dialog = new PasswordResetDialog("Reset password for " + info.getUsername(), true, false, true);
        else
            dialog = new PasswordResetDialog("Reset password for " + info.getUsername(), false, false, false);
        Optional<Pair<String, String>> result = dialog.showAndWait();
        result.ifPresent(pair -> users.resetPassword(info.getUsername(), pair.getKey(), pair.getValue()));
    }

    private void deleteUser(Users.UserInfo info) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Delete User");
        alert.setHeaderText("Delete " + info.getFullName() + " (" + info.getUsername() + ") from the system?");
        alert.setContentText("All data pertaining to the user will be lost.\nTHIS CANNOT BE UNDONE!");
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES)
            users.deleteUser(info.getUid());
    }

    @FXML
    private void addUser() {
        try {
            CreateUserDialog dialog = new CreateUserDialog(AssignGui.getInstance().getStage());
            Optional<Users.UserInfo> result = dialog.showAndWait();
            result.ifPresent(users::addUser);
        } catch (IOException e) {
            LibAssign.showExceptionError(e);
        }
    }

    @FXML
    private void importUsers() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Users");
        Boolean needPass = ServerConfig.getBoolProp(ServerConfig.SERVER_AUTH_USES_DB);
        if (needPass == null)
            needPass = true;
        alert.setContentText("Select a CSV file with the following columns: username, fullname" + (needPass ? ", password" : ""));
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().setAll(csvFilter, allFilter);
        fileChooser.setSelectedExtensionFilter(csvFilter);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileChooser.setTitle("Import Users From CSV");
        File csvFile = fileChooser.showOpenDialog(AssignGui.getInstance().getStage());
        if (csvFile != null && csvFile.exists()) {
            users.importUsers(csvFile, -1, needPass);
        }
    }

}
