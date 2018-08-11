package edu.rpi.aris.assign.server;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
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
                        System.out.println("Server shutdown requested");
                        System.exit(0);
                        break;
                    default:
                        System.err.println("Unrecognized command: " + cmd);
                        help(true, null);
                }
            }
        } catch (IOException e) {
            log.error("An error occurred on the CLI Thread", e);
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
        PrintStream out = error ? System.err : System.out;
        if (args != null && args.size() >= 1) {
            String cmd = args.get(0).toLowerCase();
            switch (cmd) {
                default:
                    out.println("There is no extended help available for the command: " + cmd);
            }
        } else {
            out.println("Aris Assign Server CLI Help:");
            out.println("\thelp - displays this dialog");
            out.println("\tstop - stops the server (aliases: exit, kill)");
        }
    }

}
