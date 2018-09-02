package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;

public interface ArisClientModule<T extends ArisModule> {

    ModuleUI<T> createModuleGui(ModuleUIOptions options) throws Exception;

    ModuleUI<T> createModuleGui(ModuleUIOptions options, Problem<T> problem) throws Exception;

}
