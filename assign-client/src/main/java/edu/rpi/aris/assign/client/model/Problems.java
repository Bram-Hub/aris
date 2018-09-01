package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.ProblemsGui;
import edu.rpi.aris.assign.message.MsgUtil;
import edu.rpi.aris.assign.message.ProblemCreateMsg;
import edu.rpi.aris.assign.message.ProblemsGetMsg;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;

import java.util.Collections;
import java.util.Date;

public class Problems implements ResponseHandler<ProblemsGetMsg> {

    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final ObservableList<Problem> problems = FXCollections.observableArrayList();
    private final ProblemsGui gui;
    private UserInfo userInfo = UserInfo.getInstance();
    private boolean loaded = false;

    public Problems(ProblemsGui gui) {
        this.gui = gui;
    }

    public void loadProblems(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            Client.getInstance().processMessage(new ProblemsGetMsg(), this);
        }
    }

    @Override
    public void response(ProblemsGetMsg message) {
        Platform.runLater(() -> {
            loadError.set(false);
            for (MsgUtil.ProblemInfo data : message.getProblems())
                problems.add(new Problem(data));
            Collections.sort(problems);
            loaded = true;
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, ProblemsGetMsg msg) {
        Platform.runLater(() -> {
            clear();
            loadError.set(true);
            userInfo.finishLoading();
            if (suggestRetry)
                loadProblems(true);
        });
    }

    public void clear() {
        problems.clear();
        loaded = false;
    }

    public ObservableList<Problem> getProblems() {
        return problems;
    }

    public boolean isLoadError() {
        return loadError.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadError;
    }

    public <T extends ArisModule> void createProblem(String name, String moduleName, edu.rpi.aris.assign.Problem<T> problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemCreateMsg<>(name, moduleName, problem), new ProblemCreateResponseHandler<>());
    }

    private class ProblemCreateResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemCreateMsg<T>> {
        @Override
        public void response(ProblemCreateMsg<T> message) {
            Platform.runLater(() -> {
                Problem problem = new Problem(message.getPid(), message.getName(), message.getModuleName(), Config.USERNAME.getValue(), new Date());
                problems.add(problem);
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ProblemCreateMsg<T> msg) {
            if (suggestRetry)
                createProblem(msg.getName(), msg.getModuleName(), msg.getProblem());
            else
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Error", "An error occurred creating the problem");
            Platform.runLater(() -> userInfo.finishLoading());
        }
    }

    public class Problem implements Comparable<Problem> {

        private final int pid;
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty module = new SimpleStringProperty();
        private final SimpleStringProperty createdBy = new SimpleStringProperty();
        private final SimpleObjectProperty<Date> createdOn = new SimpleObjectProperty<>();
        private final SimpleObjectProperty<Button> modifyColumn = new SimpleObjectProperty<>();

        public Problem(MsgUtil.ProblemInfo data) {
            this(data.pid, data.name, data.moduleName, data.createdBy, new Date(NetUtil.UTCToMilli(data.createdDateUTC)));
        }

        public Problem(int pid, String name, String module, String createdBy, Date createdOn) {
            this.pid = pid;
            this.name.set(name);
            this.module.set(module);
            this.createdBy.set(createdBy);
            this.createdOn.set(createdOn);
            Button delete = new Button("Delete");
            delete.setOnAction(event -> delete());
            modifyColumn.set(delete);
        }

        private void delete() {
            AssignGui.getInstance().notImplemented("Problem deletion");
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getModule() {
            return module.get();
        }

        public SimpleStringProperty moduleProperty() {
            return module;
        }

        public String getCreatedBy() {
            return createdBy.get();
        }

        public SimpleStringProperty createdByProperty() {
            return createdBy;
        }

        public Date getCreatedOn() {
            return createdOn.get();
        }

        public SimpleObjectProperty<Date> createdOnProperty() {
            return createdOn;
        }

        public Button getModifyButton() {
            return modifyColumn.get();
        }

        public SimpleObjectProperty<Button> modifyButtonProperty() {
            return modifyColumn;
        }

        public int getPid() {
            return pid;
        }

        @Override
        public int compareTo(Problem o) {
            int c = getName().compareTo(o.getName());
            if (c == 0)
                c = getModule().compareTo(o.getModule());
            if (c == 0)
                c = getCreatedBy().compareTo(o.getCreatedBy());
            if (c == 0)
                c = getCreatedOn().compareTo(o.getCreatedOn());
            return c;
        }

    }

}
