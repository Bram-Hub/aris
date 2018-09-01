package edu.rpi.aris.assign.message;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class DataMessage extends Message {

    public abstract void sendData(OutputStream out);

    public abstract void receiveData(InputStream in);

}
