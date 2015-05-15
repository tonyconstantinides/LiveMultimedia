package com.constantinnovationsinc.livemultimedia.callbacks;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;

import com.constantinnovationsinc.livemultimedia.utilities.ColorSpaceManager;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;


/**************************************************
 * FrameCatcher works for pre-Lollipop API
 * This class wrap the Camera Preview Window CallBack
 * so that every image is captured in this class.
 *****************************************************/
public class FrameCatcher implements Camera.PreviewCallback {
    private static final String TAG = FrameCatcher.class.getCanonicalName();
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


    /**********************************************************************
     * onPreviewFrame receives each frame video frame.
     * The frame will be color corrected and then stored in shared memory.
     * Both image formats of NV21 or YU12 is supported
     *******************************************************************/
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

    /**********************************************************************
     * getBitmapImageFromYUV returns a bitmap from an image captured in
     * the camera in YUV12 format. Image formats and video formats are not
     *  the same thing.
     *******************************************************************/
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

    /**********************************************************************
     * saveBitmap() was used to debug the code and to save each captured
     * video frame into a JPEG bitmap on your mobile device
     * use with caution as it will flood your mobile device /Picture dir
     *******************************************************************/
    public void saveBitmap(Bitmap finalBitmap) throws IllegalStateException {
        String root = Environment.getExternalStorageDirectory().toString() + "/Pictures";
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fname = "Image-" + n + ".jpg";
        File file = new File(root, fname);
        if (file.exists()) {
          boolean status = file.delete();
          if (!status) throw new IllegalStateException("Unable to delete File");
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**********************************************************************
     * startEncodingVideo - start the encoding by wraps its classes from
     * the catcher as its an implementation detail
     *******************************************************************/
    public synchronized void startEncodingVideo() {
        mRecording = false;
        mStoringVideoFrames = false;
        if (callback != null) {
            callback.startVideoEncoder();
        }
    }

}