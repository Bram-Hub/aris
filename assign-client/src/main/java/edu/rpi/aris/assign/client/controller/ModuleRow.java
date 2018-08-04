package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.EditMode;
import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.ClientModuleService;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class ModuleRow {

    @FXML
    private ImageView moduleImage;
    @FXML
    private Button moduleBtn;

    private Parent root;
    private ArisModule module;

    public ModuleRow(String moduleName) {
        module = ClientModuleService.getService().getModule(moduleName);
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("../view/module_row.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @FXML
    public void initialize() {
        try {
            moduleImage.setImage(new Image(module.getModuleIcon()));
            moduleBtn.setText("Launch " + module.getModuleName());
        } catch (ArisModuleException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @FXML
    public void launchModule() {
        try {
            module.getClientModule().createModuleGui(EditMode.UNRESTRICTED, null).show();
        } catch (ArisModuleException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public Parent getRoot() {
        return root;
    }

}
