package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.ModuleService;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.ProblemsGui;
import edu.rpi.aris.assign.message.*;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Problems implements ResponseHandler<ProblemsGetMsg> {

    private static final File problemStorageDir = new File(LocalConfig.CLIENT_STORAGE_DIR, "problems");
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final ObservableList<Problem> problems = FXCollections.observableArrayList();
    private final HashMap<Integer, Problem> problemMap = new HashMap<>();
    private final ProblemsGui gui;
    private final ProblemRenameResponseHandler renameHandler = new ProblemRenameResponseHandler();
    private final ProblemDeleteResponseHandler deleteHandler = new ProblemDeleteResponseHandler();
    private final ReentrantLock lock = new ReentrantLock(true);
    private UserInfo userInfo = UserInfo.getInstance();
    private boolean loaded = false;
    private HashSet<Consumer<Boolean>> onLoadComplete = new HashSet<>();

    public Problems(ProblemsGui gui) {
        this.gui = gui;
    }

    public synchronized void loadProblems(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            clear();
            Client.getInstance().processMessage(new ProblemsGetMsg(), this);
        }
    }

    public void addOnLoadComplete(Consumer<Boolean> onLoad) {
        onLoadComplete.add(onLoad);
    }

    public void removeOnLoadComplete(Consumer<Boolean> onLoad) {
        onLoadComplete.remove(onLoad);
    }

    @Override
    public void response(ProblemsGetMsg message) {
        Platform.runLater(() -> {
            loadError.set(false);
            for (MsgUtil.ProblemInfo data : message.getProblems()) {
                Problem prob = new Problem(data);
                problems.add(prob);
                problemMap.put(prob.getPid(), prob);
            }
            Collections.sort(problems);
            loaded = true;
            userInfo.finishLoading();
            onLoadComplete.forEach(c -> c.accept(true));
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
            else
                onLoadComplete.forEach(c -> c.accept(false));
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public synchronized void clear() {
        problems.clear();
        problemMap.clear();
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

    public void renamed(Problem problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemEditMsg(problem.getPid(), problem.getName()), renameHandler);
    }

    public void delete(Problem problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemDeleteMsg(problem.getPid()), deleteHandler);
    }

    public <T extends ArisModule> void saveLocalModification(Problem problemInfo, edu.rpi.aris.assign.Problem<T> problem, ArisModule<T> module) {
        if (problemStorageDir.exists() && !problemStorageDir.isDirectory()) {
            if (!problemStorageDir.delete()) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "Failed to create problem storage directory");
                return;
            }
        }
        if (!problemStorageDir.exists()) {
            if (!problemStorageDir.mkdirs()) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "Failed to create problem storage directory");
                return;
            }
        }
        File saveFile = new File(problemStorageDir, String.valueOf(problemInfo.getPid()));
        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            module.getProblemConverter().convertProblem(problem, fos, false);
        } catch (Exception e) {
            AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "An error occurred while trying to save the file locally");
        }

    }

    public <T extends ArisModule> void uploadModifiedProblem(Problem problemInfo, edu.rpi.aris.assign.Problem<T> problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemEditMsg<>(problemInfo.getPid(), problemInfo.getModule(), problem), new ProblemModifyResponseHandler<>(problemInfo));
    }

    private <T extends ArisModule> void fetchAndModify(int pid, ArisModule<T> module) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemFetchMessage<>(pid, module.getModuleName()), new ProblemFetchResponseHandler<>(module));
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Problem getProblem(int pid) {
        return problemMap.get(pid);
    }

    private class ProblemCreateResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemCreateMsg<T>> {
        @Override
        public void response(ProblemCreateMsg<T> message) {
            Platform.runLater(() -> {
                Problem problem = new Problem(message.getPid(), message.getName(), message.getModuleName(), LocalConfig.USERNAME.getValue(), new Date());
                problems.add(problem);
                problemMap.put(problem.getPid(), problem);
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ProblemCreateMsg<T> msg) {
            if (suggestRetry)
                createProblem(msg.getName(), msg.getModuleName(), msg.getProblem());
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ProblemRenameResponseHandler implements ResponseHandler<ProblemEditMsg> {

        @Override
        public void response(ProblemEditMsg message) {
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public void onError(boolean suggestRetry, ProblemEditMsg msg) {
            if (suggestRetry)
                renamed(problemMap.get(msg.getPid()));
            else {
                loadProblems(true);
            }
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ProblemModifyResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemEditMsg<T>> {

        private final Problem problemInfo;

        public ProblemModifyResponseHandler(Problem problemInfo) {
            this.problemInfo = problemInfo;
        }

        @Override
        public void response(ProblemEditMsg<T> message) {
            Platform.runLater(() -> userInfo.finishLoading());
            File saveFile = new File(problemStorageDir, String.valueOf(problemInfo.getPid()));
            if (saveFile.exists())
                if (!saveFile.delete())
                    LibAssign.showExceptionError(new IOException("Failed to delete local problem file: " + saveFile.getAbsolutePath()));
        }

        @Override
        public void onError(boolean suggestRetry, ProblemEditMsg<T> msg) {
            if (suggestRetry)
                uploadModifiedProblem(problemInfo, msg.getProblem());
            else {
                ArisModule<T> module = ModuleService.getService().getModule(msg.getModuleName());
                if (module != null) {
                    try {
                        gui.modifyProblem(problemInfo, msg.getProblem(), module);
                    } catch (Exception e) {
                        LibAssign.showExceptionError(e);
                    }
                } else
                    AssignClient.getInstance().getMainWindow().displayErrorMsg("Missing module", "Unable to find module \"" + msg.getModuleName() + "\"");
            }
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ProblemDeleteResponseHandler implements ResponseHandler<ProblemDeleteMsg> {

        @Override
        public void response(ProblemDeleteMsg message) {
            Platform.runLater(() -> {
                Problem prob = problemMap.remove(message.getPid());
                problems.remove(prob);
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ProblemDeleteMsg msg) {
            if (suggestRetry)
                delete(problemMap.get(msg.getPid()));
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ProblemFetchResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemFetchMessage<T>> {

        private final ArisModule<T> module;

        public ProblemFetchResponseHandler(ArisModule<T> module) {
            this.module = module;
        }

        @Override
        public void response(ProblemFetchMessage<T> message) {
            Platform.runLater(() -> {
                Problem problemInfo = problemMap.get(message.getPid());
                if (problemInfo != null) {
                    try {
                        gui.modifyProblem(problemInfo, message.getProblem(), module);
                    } catch (Exception e) {
                        LibAssign.showExceptionError(e);
                    }
                }
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ProblemFetchMessage<T> msg) {
            if (suggestRetry)
                fetchAndModify(msg.getPid(), module);
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class Problem implements Comparable<Problem> {

        private final int pid;
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty module = new SimpleStringProperty();
        private final SimpleStringProperty createdBy = new SimpleStringProperty();
        private final SimpleStringProperty createdOn = new SimpleStringProperty();
        private final SimpleObjectProperty<Date> createdOnDate = new SimpleObjectProperty<>();
        private final SimpleObjectProperty<Node> modifyColumn = new SimpleObjectProperty<>();

        public Problem(MsgUtil.ProblemInfo data) {
            this(data.pid, data.name, data.moduleName, data.createdBy, new Date(NetUtil.UTCToMilli(data.createdDateUTC)));
        }

        public Problem(int pid, String name, String module, String createdBy, Date createdOn) {
            this.pid = pid;
            this.name.set(name);
            this.module.set(module);
            this.createdBy.set(createdBy);
            this.createdOn.bind(Bindings.createStringBinding(() -> createdOnDate.get() == null ? null : AssignGui.DATE_FORMAT.format(createdOnDate.get()), this.createdOnDate));
            createdOnDate.set(createdOn);
            HBox box = new HBox(5);
            Button modify = new Button("Modify");
            modify.setOnAction(event -> modify());
            Button delete = new Button("Delete");
            delete.setOnAction(event -> delete());
            box.getChildren().addAll(modify, delete);
            box.setAlignment(Pos.CENTER);
            modifyColumn.set(box);
        }

        private <T extends ArisModule> void modify() {
            File localFile = new File(problemStorageDir, String.valueOf(pid));
            ArisModule<T> module = ModuleService.getService().getModule(getModule());
            if (module == null) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Missing Module", "Client is missing \"" + getModule() + "\" module");
                return;
            }
            if (localFile.exists()) {
                try (FileInputStream fis = new FileInputStream(localFile)) {
                    ProblemConverter<T> converter = module.getProblemConverter();
                    gui.modifyProblem(this, converter.loadProblem(fis, false), module);
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            } else {
                fetchAndModify(pid, module);
            }
        }

        private void delete() {
            if (gui.confirmDelete(getName()))
                Problems.this.delete(this);
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

        public String getCreatedOn() {
            return createdOn.get();
        }

        public SimpleStringProperty createdOnProperty() {
            return createdOn;
        }

        public Node getModifyNode() {
            return modifyColumn.get();
        }

        public SimpleObjectProperty<Node> modifyButtonProperty() {
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

        @Override
        public String toString() {
            return getName();
        }
    }

}
