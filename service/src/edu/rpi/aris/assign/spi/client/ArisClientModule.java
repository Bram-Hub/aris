package edu.rpi.aris.assign.spi.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.EditMode;
import edu.rpi.aris.assign.ModuleUI;

public interface ArisClientModule {

    @NotNull
    ModuleUI createModuleGui(@NotNull EditMode editMode, @Nullable String description) throws ArisModuleException;

}
