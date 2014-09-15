package com.constantinnovationsinc.livemultimedia.threads;

import android.os.Process;
import android.os.HandlerThread;

public class AudioEncoderThread extends HandlerThread {
    public AudioEncoderThread(String name) {
        super(name, Process.THREAD_PRIORITY_AUDIO);
    }

    @Override
    public void run() {
        super.run();
    }

}
