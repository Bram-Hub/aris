package edu.rpi.aris.assign;

import edu.rpi.aris.assign.spi.ArisModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;


public class ModuleService {

    private static final Logger logger = LogManager.getLogger(ModuleService.class);
    private static ModuleService service = new ModuleService();
    private ServiceLoader<ArisModule> loader;
    private HashMap<String, ArisModule> services = new HashMap<>();
    private ArrayList<String> moduleNames = new ArrayList<>();
    private File moduleDirectory;
    private boolean isServer = false;

    private ModuleService() {
    }

    private static void addJarsFromDir(File dir, HashSet<URL> jars) throws MalformedURLException {
        if (dir.exists() && dir.isDirectory()) {
            File[] moduleJars = dir.listFiles(pathname -> !pathname.isDirectory() && pathname.getName().toLowerCase().endsWith(".jar"));
            if (moduleJars != null) {
                for (File f : moduleJars) {
                    jars.add(f.toURI().toURL());
                }
            }
        }
    }

    public static ModuleService getService() {
        return service;
    }

    synchronized void initializeService(File directory, boolean isServer) {
        moduleDirectory = directory;
        this.isServer = isServer;
        try {
            init();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    private synchronized void init() throws Exception {
        logger.info("Initializing ServiceLoader");
        HashSet<URL> jars = new HashSet<>();
        try {
            if (moduleDirectory == null) {
                LibAssign.getInstance().showExceptionError(Thread.currentThread(), new NullPointerException("Module Directory has not been set"), true);
                return;
            }
            addJarsFromDir(moduleDirectory, jars);
            addJarsFromDir(new File(moduleDirectory, "libs"), jars);
            URL[] urls = new URL[jars.size()];
            jars.toArray(urls);
            URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
            loader = ServiceLoader.load(ArisModule.class, classLoader);
        } catch (MalformedURLException e) {
            throw new ArisModuleException("Failed to initialize ClassLoader", e);
        }
        services.clear();
        moduleNames.clear();
        logger.info("Loading modules");
        for (ArisModule s : loader) {
            logger.info("Found module \"" + s.getModuleName() + "\"");
            if (isServer) {
                ArisServerModule m = s.getServerModule();
                if (m == null) {
                    logger.warn("Module \"" + s.getModuleName() + "\" did not supply a server module. Skipping");
                    continue;
                }
            } else {
                ArisClientModule m = s.getClientModule();
                if (m == null) {
                    logger.warn("Module \"" + s.getModuleName() + "\" did not supply a client module. Skipping");
                    continue;
                }
            }
            if (services.put(s.getModuleName(), s) != null) {
                logger.fatal("Multiple modules have been found using the name \"" + s.getModuleName() + "\"");
                logger.fatal("Either remove the extra modules or rename them");
                throw new ArisModuleException("Multiple modules detected with the same name + \"" + s.getModuleName() + "\"");
            }
        }
        moduleNames.addAll(services.keySet());
        Collections.sort(moduleNames);
    }

    public synchronized void reloadModules() throws Exception {
        logger.info("Module reload requested");
        loader.reload();
        init();
    }

    public synchronized <T extends ArisModule> ArisModule<T> getModule(String moduleName) {
        try {
            @SuppressWarnings("unchecked") ArisModule<T> module = services.get(moduleName);
            return module;
        } catch (Throwable e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
            return null;
        }
    }

    public synchronized <T extends ArisModule> ArisClientModule<T> getClientModule(String moduleName) {
        try {
            ArisModule<T> module = getModule(moduleName);
            if (module == null)
                return null;
            return module.getClientModule();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
            return null;
        }
    }

    public synchronized <T extends ArisModule> ArisServerModule<T> getServerModule(String moduleName) {
        try {
            ArisModule<T> module = getModule(moduleName);
            if (module == null)
                return null;
            return module.getServerModule();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
            return null;
        }
    }

    public synchronized List<String> moduleNames() {
        return moduleNames;
    }

}
