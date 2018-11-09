package edu.rpi.aris.assign.spi;

import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

/**
 * This is the base interface for any modules looking to be added to the Aris Assign platform. To have your module
 * recognized by Aris Assign you must first implement this interface then you must add a file as specified in the java
 * {@link java.util.ServiceLoader} framework. To do this create a file as follows:
 * META-INF/services/edu.rpi.aris.assign.spi.ArisModule and as the only line in this file put the fully qualified name
 * of your ArisModule implementation
 *
 * @param <T> the type of your aris module. (ie: if you have a class extending ArisModule called MyModule then T should
 *            be MyModule)
 * @see java.util.ServiceLoader
 */
public interface ArisModule<T extends ArisModule> {

    /**
     * Gets the name of this module. This name should be unique among all other modules
     * and this should always return the same string for a given module regardless of version as the name is used to
     * uniquely identify this module
     *
     * @return the module name
     */
    String getModuleName();

    /**
     * Gets the client side portion of this module ({@link ArisClientModule}). This can be null if the server side
     * implementation is separate from the client side implementation
     *
     * @return the {@link ArisClientModule} for this {@link ArisModule} or null if this {@link ArisModule} only contains
     * the server side implementation
     * @throws Exception for any error that may occur while trying to get the client module
     * @see ArisClientModule
     */
    @Nullable
    ArisClientModule<T> getClientModule() throws Exception;

    /**
     * Gets the server side portion of this module ({@link ArisServerModule}). This can be null if the server side implementation is separate
     * from the client side implementation
     *
     * @return the {@link ArisServerModule} for this {@link ArisModule} or null if this {@link ArisModule} only contains
     * the client side implementation
     * @throws Exception for any error that may occur while trying to get the server module
     * @see ArisServerModule
     */
    @Nullable
    ArisServerModule<T> getServerModule() throws Exception;

    /**
     * Gets the {@link ProblemConverter} used to write a {@link edu.rpi.aris.assign.Problem} to an {@link java.io.OutputStream}
     * and to read a {@link edu.rpi.aris.assign.Problem} from an {@link InputStream}
     *
     * @return the {@link ProblemConverter} for this {@link ArisModule}
     * @throws Exception for any error that may occur while trying to get the {@link ProblemConverter}
     * @see ProblemConverter
     * @see edu.rpi.aris.assign.Problem
     */
    ProblemConverter<T> getProblemConverter() throws Exception;

    /**
     * A set of properties passed from the Assign client/server for the module to optionally use.
     * (Not yet implemented)
     *
     * @param properties the map of aris properties
     */
    void setArisProperties(@NotNull HashMap<String, String> properties);

    /**
     * Gets an {@link InputStream} to read the icon for this {@link ArisModule}. This is here so Aris Assign can show your
     * program's icon within it's UI. This cannot be null
     *
     * @return An {@link InputStream} for the module icon
     * @throws Exception for any error that occurs while trying to load the module icon
     */
    @NotNull
    InputStream getModuleIcon() throws Exception;

    /**
     * Gets a list of file extensions supported by this {@link ArisModule}. These are used to know what files can be
     * imported as problems for this module. Note: the file extensions should NOT contain the dot (ie: txt, png, jpg)
     *
     * @return the list of file extensions supported by this {@link ArisModule}
     * @throws Exception for any error that may occur while trying to get the file extensions
     */
    @NotNull
    List<String> getProblemFileExtensions() throws Exception;

}
