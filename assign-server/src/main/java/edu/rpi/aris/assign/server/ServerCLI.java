package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.LibAssign;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;

public class ServerCLI {

    private static final Logger log = LogManager.getLogger();
    private static Thread cliThread;

    public static synchronized void startCliThread() {
        if (cliThread != null)
            return;
        cliThread = new Thread(ServerCLI::cli, "CLI Thread");
        cliThread.setDaemon(true);
        cliThread.start();
    }

    private static void cli() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("Command: " + line);
                Pair<String, ArrayList<String>> split = splitArgs(line);
                String cmd = split.getKey().toLowerCase();
                ArrayList<String> args = split.getValue();
                switch (cmd) {
                    case "help":
                        help(false, args);
                        break;
                    case "exit":
                    case "stop":
                    case "kill":
                        log.info("Server shutdown requested");
                        System.exit(0);
                        break;
                    case "useradd":
                        if (args.size() != 1)
                            log.error("Usage: useradd <username>");
                        else {
                            try {
                                if (AssignServerMain.getServer().addUser(args.get(0), DatabaseManager.DEFAULT_ADMIN_PASS, args.get(0), AssignServerMain.getServer().getPermissions().getAdminRole(), true))
                                    log.info("User Added with password \"" + DatabaseManager.DEFAULT_ADMIN_PASS + "\"");
                                else
                                    log.error("Failed to add user");
                            } catch (SQLException e) {
                                LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
                            }
                        }
                        break;
                    case "userlist":
                        try {
                            log.info("Users:");
                            for (Pair<String, String> p : AssignServerMain.getServer().getDbManager().getUsers()) {
                                log.info(p.getKey() + " - " + p.getRight());
                            }
                        } catch (SQLException e) {
                            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
                        }
                        break;
                    case "rmuser":
                        log.error("rmuser not implemented");
                        break;
                    case "rlperm":
                        log.info("Reloading permissions from database...");
                        try {
                            AssignServerMain.getServer().getPermissions().reloadPermissions(AssignServerMain.getServer().getDbManager().getConnection());
                            log.info("Permissions reloaded");
                        } catch (SQLException e) {
                            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
                        }
                        break;
                    default:
                        log.error("Unrecognized command: " + cmd);
                        help(true, null);
                }
            }
        } catch (IOException e) {
            log.error("Error reading from CLI", e);
        } finally {
            log.error("CLI Thread has stopped");
        }
    }

    private static Pair<String, ArrayList<String>> splitArgs(String line) {
        line = line.trim();
        ArrayList<String> args = new ArrayList<>();
        char[] chars = line.toCharArray();
        int start = 0;
        boolean findStart = false;
        for (int i = 0; i < chars.length; ++i) {
            char c = chars[i];
            if (c == ' ' || c == '\t') {
                if (!findStart) {
                    args.add(line.substring(start, i));
                    findStart = true;
                }
            } else if (findStart) {
                findStart = false;
                start = i;
            }
        }
        args.add(line.substring(start));
        return new ImmutablePair<>(args.remove(0), args);
    }

    private static void help(boolean error, ArrayList<String> args) {
        Level lvl = error ? Level.ERROR : Level.INFO;
        if (args != null && args.size() >= 1) {
            String cmd = args.get(0).toLowerCase();
            switch (cmd) {
                default:
                    log.log(lvl, "There is no extended help available for the command: " + cmd);
            }
        } else {
            log.log(lvl, "Aris Assign Server CLI Help:");
            log.log(lvl, "\thelp               - displays this dialog");
            log.log(lvl, "\tstop               - stops the server (aliases: exit, kill)");
            log.log(lvl, "\tuseradd <username> - adds the specified user");
            log.log(lvl, "\tuserlist           - lists the users for this server");
            log.log(lvl, "\trmuser <username>  - deletes the given user from the server");
            log.log(lvl, "\trlperm             - reloads the permissions from the database");
        }
    }

}
