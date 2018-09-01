package edu.rpi.aris.assign.spi;

import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public interface ArisModule<T extends ArisModule> {

    String getModuleName() throws Exception;

    ArisClientModule<T> getClientModule() throws Exception;

    ArisServerModule<T> getServerModule() throws Exception;

    ProblemConverter<T> getProblemConverter() throws Exception;

    void setArisProperties(HashMap<String, String> properties) throws Exception;

    InputStream getModuleIcon() throws Exception;

    List<String> getProblemFileExtensions() throws Exception;

}
