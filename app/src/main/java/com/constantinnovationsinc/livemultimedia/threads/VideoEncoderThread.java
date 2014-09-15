package com.constantinnovationsinc.livemultimedia.threads;

import android.os.HandlerThread;

/**
 * Created by constantinnovationsinc on 9/1/14.
 */
public class VideoEncoderThread  extends HandlerThread {

    public VideoEncoderThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        super.run();

    }
}
