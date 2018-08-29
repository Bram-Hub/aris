package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.MainCallbackListener;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class AssignServerMain implements MainCallbackListener {

    public static final AssignServerMain instance = new AssignServerMain();
    private static AssignServer server;
    private static Logger logger = LogManager.getLogger(AssignServerMain.class);

    public static void main(String[] args) throws IOException {
        ServerConfig.getInstance();
        LibAssign.getInstance().init(true, args, instance);
    }

    public static AssignServer getServer() {
        return server;
    }

    @Override
    public void processAlreadyRunning(CommandLine cmd) throws IOException {
        logger.info("Sending message to running program");
        if (cmd.hasOption("add-user") && cmd.hasOption("password")) {
            LibAssign.getInstance().sendIpcMessage("add-user " + cmd.getOptionValue("add-user") + " " + cmd.getOptionValue("password"));
        }
        if (cmd.hasOption('u')) {
            LibAssign.getInstance().sendIpcMessage("update");
        }
    }

    @Override
    public void finishInit(CommandLine cmd) throws IOException {
        int port = -1;
        if (cmd.hasOption('p')) {
            String portStr = cmd.getOptionValue('p');
            boolean error = false;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                error = true;
            }
            if (error || port <= 0 || port > 65535) {
                logger.error("Invalid port specified: " + portStr);
                System.exit(1);
            }
        }
        String ca = cmd.getOptionValue("ca");
        String key = cmd.getOptionValue("key");
        if (ca != null && key == null)
            throw new IOException("CA certificate specified without private key");
        else if (ca == null && key != null)
            throw new IOException("Private key specified without CA certificate");
        File caFile = ca == null ? null : new File(ca);
        File keyFile = key == null ? null : new File(key);
        server = new AssignServer(port > 0 ? port : LibAssign.DEFAULT_PORT, caFile, keyFile);
        if (cmd.hasOption('u')) {
            if (!server.checkUpdate())
                System.exit(1);
        } else {
            new Thread(server, "ServerSocket-Listen").start();
            ServerCLI.startCliThread();
        }
    }

    @Override
    public void processIpcMessage(String msg) {
        //TODO
    }

}
