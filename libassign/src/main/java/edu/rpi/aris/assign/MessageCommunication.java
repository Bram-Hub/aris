package edu.rpi.aris.assign;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.rpi.aris.assign.message.ErrorMsg;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public interface MessageCommunication {

//    JsonElement readMessage() throws IOException;
//
//    void sendMessage(JsonElement msg) throws IOException;

    JsonReader getReader();

    JsonWriter getWriter();

    DataInputStream getInputStream();

    DataOutputStream getOutputStream();

    void handleErrorMsg(ErrorMsg msg);

}
