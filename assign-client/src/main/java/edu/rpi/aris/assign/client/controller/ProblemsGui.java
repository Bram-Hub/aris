package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.dialog.ProblemDialog;
import edu.rpi.aris.assign.client.model.Problems;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.IOException;
import java.util.Date;

public class ProblemsGui implements TabGui {

    @FXML
    private TableView<Problems.Problem> problemTbl;
    @FXML
    private TableColumn<Problems.Problem, String> name;
    @FXML
    private TableColumn<Problems.Problem, String> module;
    @FXML
    private TableColumn<Problems.Problem, String> createdBy;
    @FXML
    private TableColumn<Problems.Problem, Date> createdOn;
    @FXML
    private TableColumn<Problems.Problem, Button> delete;

    private UserInfo userInfo = UserInfo.getInstance();
    private Problems problems = new Problems(this);
    private Parent root;

    public ProblemsGui() {
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("../view/problems_view.fxml"));
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

    @Override
    public boolean isPermanentTab() {
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
    }

    @FXML
    public void createProblem() {
        try {
            ProblemDialog dialog = new ProblemDialog(AssignGui.getInstance().getStage());
            dialog.showAndWait();
        } catch (IOException e) {
            LibAssign.showExceptionError(e);
        }
    }

}
