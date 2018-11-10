package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;

/**
 * This is the server side portion of an {@link ArisModule}. This must be implemented on the server side of your module
 *
 * @param <T> the {@link ArisModule} this {@link ArisServerModule} is part of
 */
public interface ArisServerModule<T extends ArisModule> {

    /**
     * Returns the {@link AutoGrader} for this {@link ArisModule}
     *
     * @return the {@link AutoGrader}
     * @see AutoGrader
     */
    @NotNull
    AutoGrader<T> getAutoGrader();

}
