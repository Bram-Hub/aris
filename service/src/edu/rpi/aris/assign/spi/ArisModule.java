package edu.rpi.aris.assign.spi;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;

import java.io.InputStream;
import java.util.HashMap;

public interface ArisModule {

    @NotNull
    String getModuleName() throws ArisModuleException;

    @Nullable
    ArisClientModule getClientModule() throws ArisModuleException;

    @Nullable
    ArisServerModule getServerModule() throws ArisModuleException;

    @NotNull
    ProblemConverter getProblemConverter() throws ArisModuleException;

    void setArisProperties(@NotNull HashMap<String, String> properties);

    InputStream getModuleIcon() throws ArisModuleException;

}
