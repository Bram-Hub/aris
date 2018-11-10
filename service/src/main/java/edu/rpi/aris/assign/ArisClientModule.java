package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is the client side portion of an {@link ArisModule}. This must be implemented on the client side of your module
 *
 * @param <T> the {@link ArisModule} this {@link ArisClientModule} is part of
 */
public interface ArisClientModule<T extends ArisModule> {

    /**
     * Returns a {@link ModuleUI} for this {@link ArisModule} T with the given configuration options
     *
     * @param options the {@link ModuleUIOptions} for this module
     * @return the created {@link ModuleUI}
     * @throws Exception for any exception that occurs while creating the {@link ModuleUI}
     * @see ModuleUI
     * @see ModuleUIOptions
     */
    @NotNull
    ModuleUI<T> createModuleGui(@NotNull ModuleUIOptions options) throws Exception;

    /**
     * Returns a {@link ModuleUI} for this {@link ArisModule} T with the given configuration options displaying the given
     * problem
     *
     * @param options the {@link ModuleUIOptions} for this module
     * @param problem the {@link Problem} to be displayed in the {@link ModuleUI}. If problem is null then this should act
     *                the same as {@link ArisClientModule#createModuleGui(ModuleUIOptions)}
     * @return the created {@link ModuleUI}
     * @throws Exception for any exception that occurs while creating the {@link ModuleUI}
     * @see ModuleUI
     * @see ModuleUIOptions
     * @see Problem
     */
    @NotNull
    ModuleUI<T> createModuleGui(@NotNull ModuleUIOptions options, @Nullable Problem<T> problem) throws Exception;

}
