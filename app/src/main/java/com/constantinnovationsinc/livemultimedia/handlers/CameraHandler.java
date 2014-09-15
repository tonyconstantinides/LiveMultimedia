package com.constantinnovationsinc.livemultimedia.handlers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class CameraHandler extends Handler {
    public CameraHandler(Looper myLooper) {
        super(myLooper);
    }
    public void handleMessage(Message msg) {
        if (msg != null) {
            Bundle bundle =  msg.getData();
        }
    }
}