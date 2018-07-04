package edu.rpi.aris.assign;

import edu.rpi.aris.assign.message.ErrorMsg;

import java.io.IOException;

public interface MessageCommunication {

    String readMessage() throws IOException;

    void sendMessage(String msg) throws IOException;

    void handleErrorMsg(ErrorMsg msg);

}
