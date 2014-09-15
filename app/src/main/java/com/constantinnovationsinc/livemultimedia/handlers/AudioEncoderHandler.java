package com.constantinnovationsinc.livemultimedia.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;

public class AudioEncoderHandler extends Handler {
    public AudioEncoderHandler(Looper myLooper) {
        super(myLooper );
    }
    public void handleMessage(Message msg) {
        if (msg != null) {
           Bundle bundle =  msg.getData();
        }
    }
}
