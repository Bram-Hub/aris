package edu.rpi.aris.assign;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import javafx.stage.Modality;
import javafx.stage.Window;

public interface ModuleUI {

    void show() throws ArisModuleException;

    void hide() throws ArisModuleException;

    void setModal(@NotNull Modality modality, @Nullable Window owner) throws ArisModuleException;

    void setDescription(@Nullable String description) throws ArisModuleException;

    void addCloseListener(@NotNull Runnable runnable) throws ArisModuleException;

    void removeCloseListener(@NotNull Runnable runnable) throws ArisModuleException;

    @NotNull
    Problem getProblem() throws ArisModuleException;

}
