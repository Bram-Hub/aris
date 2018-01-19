package edu.rpi.aris;

public class ArisNative {

    static {
        System.loadLibrary("aris");
    }

    private native void test();

}
