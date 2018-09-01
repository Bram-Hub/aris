package edu.rpi.aris.assign;

import edu.rpi.aris.assign.message.ErrorMsg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface MessageCommunication {

    String readMessage() throws IOException;

    void sendMessage(String msg) throws IOException;

    InputStream getInputStream();

    OutputStream getOutputStream();

    void handleErrorMsg(ErrorMsg msg);

}
