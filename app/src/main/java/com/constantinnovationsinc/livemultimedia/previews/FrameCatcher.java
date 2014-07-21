package com.constantinnovationsinc.livemultimedia.previews;

import android.hardware.Camera;
import android.util.Log;

import java.nio.ByteBuffer;
import com.constantinnovationsinc.livemultimedia.encoders.EncodingThread;

public class FrameCatcher implements Camera.PreviewCallback {
 private static final String TAG = FrameCatcher.class.getName();
 public int mFrames = 0;
 private final long mExpectedSize;

 public FrameCatcher(long width, long height) {
     mExpectedSize = (width * height * 3 / 2);
 }
 
 @Override
 public void onPreviewFrame(byte[] data, Camera camera) {
	 	if (data == null) {
	 		Log.e(TAG, "Recived Null video frame data!");
	 		return;
	 	}
	    if (mExpectedSize != data.length) {
         Log.e(TAG,  "Bad frame size, got " + data.length + " expected " + mExpectedSize);
         return;
     }
    mFrames++;
    Log.d(TAG, "Frames received: " + mFrames);
    byte[] value = new byte[data.length];
    System.arraycopy(data,  0,  value,  0,  data.length);
    EncodingThread.mVideoFrameList.add(value);
    camera.addCallbackBuffer(data);
   // EncodingThread.mVideoFrameList.add(data);
   }  
}