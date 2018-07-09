package edu.rpi.aris.assign;

import org.apache.commons.cli.CommandLine;

import java.io.IOException;

public interface MainCallbackListener {

    void processAlreadyRunning(CommandLine cmd) throws IOException;

    void finishInit(CommandLine cmd) throws IOException;

    void processIpcMessage(String msg);

}
