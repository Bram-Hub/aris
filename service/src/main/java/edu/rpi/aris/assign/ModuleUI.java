package edu.rpi.aris.assign;

import javafx.stage.Modality;
import javafx.stage.Window;

public interface ModuleUI {

    void show() throws ArisModuleException;

    void hide() throws ArisModuleException;

    void setModal(Modality modality, Window owner) throws ArisModuleException;

    void setDescription(String description) throws ArisModuleException;

    void addCloseListener(Runnable runnable) throws ArisModuleException;

    void removeCloseListener(Runnable runnable) throws ArisModuleException;

    Problem getProblem() throws ArisModuleException;

}
