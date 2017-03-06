package com.nordicsemi.nrfUARTv2.radio;

public class FmRadioNative {
    static native short activeAf();

    static native short[] autoScan();

    static native boolean closeDev();

    static native short[] emcmd(short[] sArr);

    static native boolean emsetth(int i, int i2);

    static native boolean getFmStatus(int i);

    static native int[] getHardwareVersion();

    static native byte[] getLrText();

    static native byte[] getPs();

    static native int isRdsSupport();

    static native boolean openDev();

    static native boolean powerDown(int i);

    static native boolean powerUp(float f);

    static native short readCapArray();

    static native short readRds();

    static native short readRdsBler();

    static native int readRssi();

    static native float seek(float f, boolean z);

    static native boolean setFmStatus(int i, boolean z);

    static native int setMute(boolean z);

    static native int setRds(boolean z);

    static native boolean setStereoMono(boolean z);

    static native boolean stereoMono();

    static native boolean stopScan();

    static native int switchAntenna(int i);

    static native boolean tune(float f);

    static {
        System.loadLibrary("fmjni");
    }
}
