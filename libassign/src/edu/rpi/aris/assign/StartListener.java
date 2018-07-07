package edu.rpi.aris.assign;

import org.apache.commons.cli.CommandLine;

import java.io.IOException;

public interface StartListener {

    void processAlreadyRunning(CommandLine cmd) throws IOException;

    void finishInit(CommandLine cmd) throws IOException;

}
