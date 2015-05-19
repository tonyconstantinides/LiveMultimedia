/*
*   Copyright 2015 Constant Innovations Inc
*
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/
package com.constantinnovationsinc.livemultimedia.app;
import android.app.Application;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/*****************************************************
 * I use the App Object to store Audio sampling data
 *****************************************************/
public class MultimediaApp extends Application {
    private static final String TAG = MultimediaApp.class.getCanonicalName();
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
        mSavedAudioData = new ArrayBlockingQueue<>(150);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.e(TAG, "Low Memory Warning!");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.e(TAG, "App terminating!");
    }

}
