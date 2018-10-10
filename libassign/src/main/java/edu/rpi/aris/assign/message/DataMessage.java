package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class DataMessage extends Message {

    protected DataMessage(Perm permission) {
        super(permission);
    }

    protected DataMessage(Perm permission, boolean customPermCheck) {
        super(permission, customPermCheck);
    }

    public abstract void sendData(DataOutputStream out) throws Exception;

    public abstract void receiveData(DataInputStream in) throws Exception;

}
