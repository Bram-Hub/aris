package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;

public interface ArisClientModule<T extends ArisModule> {

    ModuleUI<T> createModuleGui(EditMode editMode, String description) throws Exception;

    ModuleUI<T> createModuleGui(EditMode editMode, String description, Problem<T> problem) throws Exception;

}
