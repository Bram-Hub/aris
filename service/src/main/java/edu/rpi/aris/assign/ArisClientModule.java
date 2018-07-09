package edu.rpi.aris.assign;

public interface ArisClientModule {

    ModuleUI createModuleGui(EditMode editMode, String description) throws ArisModuleException;

}
