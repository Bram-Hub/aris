package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class ModuleRow {

    public static final ModuleUIOptions UI_OPTIONS = new ModuleUIOptions(EditMode.UNRESTRICTED_EDIT, null, true, true, false, false, true);
    @FXML
    private ImageView moduleImage;
    @FXML
    private Button moduleBtn;

    private Parent root;
    private ArisModule module;

    public ModuleRow(String moduleName) {
        module = ModuleService.getService().getModule(moduleName);
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("/edu/rpi/aris/assign/client/view/module_row.fxml"));
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
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @FXML
    public void launchModule() {
        new Thread(() -> {
            try {
                ArisClientModule m = module.getClientModule();
                if (m != null)
                    m.createModuleGui(UI_OPTIONS).show();
                else
                    LibAssign.getInstance().showExceptionError(Thread.currentThread(), new Exception("Module " + module.getModuleName() + " missing client module"), true);
            } catch (Exception e) {
                LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
            }
        }).start();
    }

    public Parent getRoot() {
        return root;
    }

}
