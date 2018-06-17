package edu.rpi.aris.net;

import edu.rpi.aris.net.message.ErrorMsg;

import java.io.IOException;

public interface MessageCommunication {

    String readMessage() throws IOException;

    void sendMessage(String msg) throws IOException;

    void handleErrorMsg(ErrorMsg msg);

}
