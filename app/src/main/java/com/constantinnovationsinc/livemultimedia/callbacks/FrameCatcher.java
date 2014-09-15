package com.constantinnovationsinc.livemultimedia.callbacks;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;
import android.graphics.ImageFormat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Environment;
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;

import com.constantinnovationsinc.livemultimedia.R;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;
import com.constantinnovationsinc.livemultimedia.utilities.ColorSpaceManager;

import java.io.IOException;


public class FrameCatcher implements Camera.PreviewCallback {
    private static final String TAG = FrameCatcher.class.getName();
    private final long mExpectedSize;
    public Boolean mRecording = false;
    public Boolean mStoringVideoFrames = false;
    public SharedVideoMemory mVideoBuffer = null;
    public int mNumFramesToBuffer = 210;
    public int mNumFramesBeforeStartingEncoders = 210;
    public int mWidth = -1;
    public int mHeight = -1;
    public FramesReadyCallback callback;
    public String mImageFormat;

    public FrameCatcher(long width, long height, String imageFormat, FramesReadyCallback receiver) {
        Log.d(TAG, "Passing width,height to FrameCacther: " + String.valueOf(width) + "," + String.valueOf(height));
        mExpectedSize = (width * height * 3 / 2);
        mWidth = (int) width;
        mHeight = (int) height;
        mImageFormat = imageFormat;
        callback = receiver;
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
            try {
                if (mVideoBuffer == null) {
                    if (callback != null) {
                        callback.playLazerSound();
                    }
                    mVideoBuffer = new SharedVideoMemory("video", data.length, data.length * mNumFramesToBuffer);
                    mVideoBuffer.lockMemory();
                    Log.w(TAG, "Write out first video frame to shared memory!");
                    if (mImageFormat != null   && !mImageFormat.equalsIgnoreCase("UNKNOWN")) {
                        if ( mImageFormat.equalsIgnoreCase("NV21")) {
                            ColorSpaceManager.NV21toYUV420SemiPlanar(data, value, mWidth, mHeight);
                        } else  if ( mImageFormat.equalsIgnoreCase("YV12")) {
                            ColorSpaceManager.YV12toYUV420PackedSemiPlanar(data, value, mWidth, mHeight);
                        }
                    }
                    mVideoBuffer.writeBytes(value, 0, 0, value.length);
                    mStoringVideoFrames = true;
                } else {
                    if (mVideoBuffer.getFrameCount() == mNumFramesBeforeStartingEncoders) {
                        startEncodingVideo();
                    } else if (mVideoBuffer.getFrameCount() <= mNumFramesToBuffer) {
                        try {
                            ColorSpaceManager.YV12toYUV420PackedSemiPlanar(data, value, mWidth, mHeight);
                            mVideoBuffer.writeBytes(value, 0, ((mVideoBuffer.getFrameCount() - 1) * value.length), value.length);
                            Log.w(TAG, " Frame " + mVideoBuffer.getFrameCount() + "color corrected!");
                            Log.w(TAG, "Written " + mVideoBuffer.getFrameCount() + " frames written out to shared memory file!");
                        } catch (IndexOutOfBoundsException e) {
                            Log.e(TAG, e.toString());
                            Log.e(TAG, "Error writing out frame number " + mVideoBuffer.getFrameCount());
                        }
                    } else {
                        Log.w(TAG, "Finished writing out video frames!");
                        mRecording = false;
                        mStoringVideoFrames = false;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public synchronized SharedVideoMemory getSharedMemFile() {
        return mVideoBuffer;
    }

    public synchronized void setRecordingState(Boolean state) {
        mRecording = state;
        if (mRecording) {
            Log.e(TAG, "Recording set to true in FrameCatcher, Runthe encoders!");
        }
    }

    public synchronized  Boolean getRecordingState() {
        return mRecording;
    }

    public synchronized Boolean isSavingVideoFrames() {
        return mStoringVideoFrames;
    }

    public static Bitmap getBitmapImageFromYUV(byte[] data, int width, int height) {
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 80, baos);
        byte[] jdata = baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
        return bmp;
    }

    public void saveBitmap(Bitmap finalBitmap) {
        String root = Environment.getExternalStorageDirectory().toString() + "/Pictures";
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fname = "Image-" + n + ".jpg";
        File file = new File(root, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void startEncodingVideo() {
        mRecording = false;
        mStoringVideoFrames = false;
        if (callback != null) {
            callback.startVideoEncoder();
        }
    }

}