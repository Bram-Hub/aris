package edu.rpi.aris.assign.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class DataMessage extends Message {

    public abstract void sendData(DataOutputStream out) throws Exception;

    public abstract void receiveData(DataInputStream in) throws Exception;

}
