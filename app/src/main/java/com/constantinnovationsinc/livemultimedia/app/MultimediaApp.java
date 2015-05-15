package com.constantinnovationsinc.livemultimedia.app;

import android.app.Application;
import android.content.ComponentCallbacks;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/*****************************************************
 * I use the App Object to store Audio sampling data
 *****************************************************/
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
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        super.registerComponentCallbacks(callback);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

    }

}
