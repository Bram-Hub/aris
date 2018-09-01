package edu.rpi.aris.assign;

import edu.rpi.aris.assign.message.ErrorMsg;

import java.io.*;

public interface MessageCommunication {

    String readMessage() throws IOException;

    void sendMessage(String msg) throws IOException;

    DataInputStream getInputStream();

    DataOutputStream getOutputStream();

    void handleErrorMsg(ErrorMsg msg);

}
