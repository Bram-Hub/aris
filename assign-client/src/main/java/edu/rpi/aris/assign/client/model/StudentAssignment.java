package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.controller.AssignGui;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;

import java.time.ZonedDateTime;
import java.util.Date;

public class StudentAssignment {

    private ObservableList<AssignedProblem> problems = FXCollections.observableArrayList();

    public StudentAssignment() {

    }

    public void loadAssignment(boolean reload) {

    }

    public class AssignedProblem extends Submission {

        public AssignedProblem(int pid, String name, String status) {
            super(pid, 0, name, null, status);
            getButton().setText("Create Submission");
        }

        @Override
        protected void buttonPushed(ActionEvent actionEvent) {

        }
    }

    public class Submission {

        private final int pid;
        private final int sid;
        private final SimpleStringProperty name;
        private final SimpleObjectProperty<ZonedDateTime> submittedOn;
        private final SimpleStringProperty submittedOnStr = new SimpleStringProperty();
        private final SimpleStringProperty status;
        private final SimpleObjectProperty<Button> button;

        public Submission(int pid, int sid, String name, ZonedDateTime submittedOn, String status) {
            this.pid = pid;
            this.sid = sid;
            this.name = new SimpleStringProperty(name);
            this.submittedOn = new SimpleObjectProperty<>(submittedOn);
            this.submittedOnStr.bind(Bindings.createStringBinding(() -> AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(submittedOn))), this.submittedOn));
            this.status = new SimpleStringProperty(status);
            Button btn = new Button("View");
            btn.setOnAction(this::buttonPushed);
            button = new SimpleObjectProperty<>(btn);
        }

        protected void buttonPushed(ActionEvent actionEvent) {

        }

        public SimpleStringProperty submittedOnProperty() {
            return submittedOnStr;
        }

        public String getSubmittedOn() {
            return submittedOnStr.get();
        }

        public int getPid() {
            return pid;
        }

        public int getSid() {
            return sid;
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getStatus() {
            return status.get();
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public Button getButton() {
            return button.get();
        }

        public SimpleObjectProperty<Button> buttonProperty() {
            return button;
        }
    }

}
