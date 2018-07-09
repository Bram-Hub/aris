package edu.rpi.aris.assign.spi;

import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;

import java.io.InputStream;
import java.util.HashMap;

public interface ArisModule {

    String getModuleName() throws ArisModuleException;

    ArisClientModule getClientModule() throws ArisModuleException;

    ArisServerModule getServerModule() throws ArisModuleException;

    ProblemConverter getProblemConverter() throws ArisModuleException;

    void setArisProperties(HashMap<String, String> properties);

    InputStream getModuleIcon() throws ArisModuleException;

}
