package com.constantinnovationsinc.livemultimedia.app;

import android.app.Application;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by constantinnovationsinc on 8/25/14.
 */
public class MultimediaApp extends Application {

    public  ArrayBlockingQueue<ByteBuffer> mSavedAudioData = null;

    public synchronized  void saveAudioData(ByteBuffer data) {
        mSavedAudioData.add(data);
    }

    public synchronized  ByteBuffer pullAudioData() {
        return mSavedAudioData.poll();
    }

    public synchronized  int capacity() {
        return mSavedAudioData.remainingCapacity();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSavedAudioData = new ArrayBlockingQueue<ByteBuffer>(150);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}
