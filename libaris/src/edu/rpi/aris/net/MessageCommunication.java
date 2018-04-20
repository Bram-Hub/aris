package edu.rpi.aris.net;

import java.io.IOException;

public interface MessageCommunication {

    String readMessage() throws IOException;

    void sendMessage(String msg) throws IOException;

    boolean readData(byte[] data) throws IOException;

    void sendData(byte[] data) throws IOException;

}
