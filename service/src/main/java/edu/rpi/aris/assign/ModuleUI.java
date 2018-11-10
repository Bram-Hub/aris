package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModuleUI<T extends ArisModule> {

    void show() throws Exception;

    void hide() throws Exception;

    void setModal(@NotNull Modality modality, @NotNull Window owner) throws Exception;

    void setDescription(@NotNull String description) throws Exception;

    void setModuleUIListener(@NotNull ModuleUIListener listener);

    @Nullable
    Window getUIWindow();

    @NotNull
    Problem<T> getProblem() throws Exception;

}
