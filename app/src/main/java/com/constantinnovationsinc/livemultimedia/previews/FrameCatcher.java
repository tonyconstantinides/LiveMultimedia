package com.constantinnovationsinc.livemultimedia.previews;

import android.hardware.Camera;
import android.util.Log;
import android.os.MemoryFile;

import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;

import java.io.IOException;


public class FrameCatcher implements Camera.PreviewCallback {
 private static final String TAG = FrameCatcher.class.getName();
 private final long mExpectedSize;
 public int mFrames = 0;
 public Boolean mRecording = false;
 public SharedVideoMemory mVideoBuffer = null;

 public FrameCatcher(long width, long height) {
     mExpectedSize = (width * height * 3 / 2);
 }
 
 @Override
 public void onPreviewFrame(byte[] data, Camera camera) {
     if (data == null) {
         Log.e(TAG, "Received null video frame data!");
         return;
     }
     if (mExpectedSize != data.length) {
         Log.e(TAG, "Bad frame size, got " + data.length + " expected " + mExpectedSize);
         return;
     }
     byte[] value = new byte[data.length];
     System.arraycopy(data, 0, value, 0, data.length);
     camera.addCallbackBuffer(data);
     if (mRecording) {
         mFrames++;
         Log.d(TAG, "Now recording new video frame " + mFrames);
         try {
             if (mVideoBuffer == null) {
                 mVideoBuffer = new SharedVideoMemory("video", data.length * 1800);
                 mVideoBuffer.lockMemory();
                 Log.d(TAG, "Write out first video frame to shared memory!");
                 mVideoBuffer.writeBytes(value, 0, 0, data.length);
             } else {
                 Log.d(TAG, "Write out video frame: " + mFrames);
                 if (mFrames <= 1800) {
                     mVideoBuffer.writeBytes(value, 0, ((mFrames - 1) * data.length) + 1, data.length);
                 } else {
                     Log.d(TAG, "Finished writing out video frames!");
                     mRecording = false;
                 }
             }
         } catch (IOException e) {
             Log.e(TAG, e.getMessage());
         }
     }
 }

    public SharedVideoMemory getSharedMemFile() {
        return mVideoBuffer;
    }

    public void setRecordingState(Boolean state) {
        mRecording = state;
    }
}
