package com.constantinnovationsinc.livemultimedia.cameras;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.MemoryFile;
import android.util.Log;
import java.io.IOException;
import java.util.List;

import com.constantinnovationsinc.livemultimedia.previews.FrameCatcher;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;

/**
 * Created by constantinnovationsinc on 8/6/14.
 */
public class JellyBeanCamera   {
    private static final String TAG = JellyBeanCamera.class.getName();
    private int mBitRate  = -1;
    private int mEncodingWidth = -1;
    private int mEncodingHeight = -1;
    private long mPreviewWidth = -1;
    private long mPreviewHeight = -1;
    private int mImageFormat = -1;
    private Context mContext = null;

    private static final int ENCODING_WIDTH  = 1280;
    private static final int ENCODING_HEIGHT = 960;
    private static final int BITRATE = 6000000;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    // movie length, in frames
    private static final int NUM_FRAMES = 330;               // 9 seconds of video
    private static int FRAME_RATE = 30;
    private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/media/";
    private static final String  MIME_TYPE = "video/avc";
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    public  Camera mCamera = null;
    public  VideoPreview mVideoPreview = null;
    public FrameCatcher mFrameCatcher = null;

    /*********************************************************************
     * Constructor
     * @param context - the context associated with this encoding thread
     *********************************************************************/
    public JellyBeanCamera(Context context, VideoPreview videoPreview) {
        mContext = context;
        mVideoPreview = videoPreview;
    }

    /*********************************************************************
     * startBackCamera
     * @return camera - active camera
     *********************************************************************/
    public synchronized Camera startBackCamera() {
        Log.d(TAG, "Camera about to be opened in a thread!");
        try {
            setParameters( ENCODING_WIDTH, ENCODING_HEIGHT, BITRATE);
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return mCamera;
    }

    /*********************************************************************
     * startFrontCamera
     * @return camera - active camera
     *********************************************************************/
    public synchronized Camera startFrontCamera() {
        Log.d(TAG, "Camera about to be opened in a thread!");
        try {
            setParameters(ENCODING_WIDTH, ENCODING_HEIGHT, BITRATE);
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return mCamera;
    }

    /**********************************************************
     *  stopCamera - 
     **********************************************************/
    public synchronized void  stopCamera() {
        Log.d(TAG, "Camera preview stopped and Camera released!");
        if (mCamera == null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
        }
        mCamera = null;
    }

    /**********************************************************
     *  setupPreviewWindow  encapsulates the current video
     * frame capture method
     **********************************************************/
    public synchronized void setupPreviewWindow() {
        Log.d(TAG, "Lets setup the preview Window");
        if (mCamera == null) {
            Log.e(TAG, "Camera object is Null in setupPreviewWindow");
            return;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        // set the preview size
        adjustCameraBasedOnOrientation();
        queryPreviewSettings(parameters);
        adjustPreviewSize(parameters);
        // set the frame rate and update the camera parameters
        setPreviewFrameRate(parameters, FRAME_RATE);
        mCamera.setParameters(parameters);
    }

    /**********************************************************
     * setupVideoCaptureMethod  encapsulates the current video
     * frame capture method
     **********************************************************/
    public synchronized void setupVideoCaptureMethod() {
        // set up the callback to capture the video frames
        setupVideoFrameCallback();
    }

    /**********************************************************
     * setPreviewTexture same as the camera pi but with error handling
     * @param surface surfaceTexture of the preview window
     **********************************************************/
    public  synchronized void setPreviewTexture( SurfaceTexture surface) {
        if (surface != null && mCamera != null) {
            try {
                mCamera.setPreviewTexture( surface );
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }

    /**********************************************************
     * startVideoPreview( start the preview so you cna begin capturing frames
     * @return the preview started or not
     **********************************************************/
    public synchronized Boolean startVideoPreview() {
        Camera.Parameters parameters = mCamera.getParameters();
        if (parameters != null) {
            parameters.setPreviewSize(ENCODING_WIDTH, ENCODING_HEIGHT);
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }
        return true;
    }

    /*******************************************************************
     * release stops the preview and the video capture and then safety
     *  releases the camera
     ******************************************************************/
    public synchronized void release() {
        mCamera.addCallbackBuffer(null);
        if (mFrameCatcher != null) {
            mFrameCatcher.setRecordingState(false);
            getSharedMemFile().clearMemory();
            mFrameCatcher = null;
        }
        mCamera.stopPreview();
        mCamera.release();
    }

    /************************************************************
     * adjustCameraBasedOnOrientation() ensure the view is in landscape
     ************************************************************/
    public synchronized void adjustCameraBasedOnOrientation() {
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mCamera.setDisplayOrientation(90);
        }
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mCamera.setDisplayOrientation(0);
        }
    }

    /************************************************************
     * ensure the view is in landscape
     ************************************************************/
    private static Camera.Size getBestPreviewSize(List<Camera.Size> previewSizes, int width, int height) {
        Camera.Size result = null;
        if (width == 0 || height == 0) {
            Log.e(TAG, "Width or Height of preview surface is zero!");
            return result;
        }
        for (Camera.Size size : previewSizes) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                }
            } else if (result != null) {
                int resultArea = result.width * result.height;
                int newArea = size.width * size.height;

                if (newArea > resultArea) {
                    result = size;
                }
            }
        }
        return result;
    }

    /***********************************************************
     * Sets the desired frame size and bit rate.
     * @param width
     * @param height
     * @param bitRate
     **********************************************************/
    private synchronized void setParameters(int width, int height, int bitRate) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mEncodingWidth = width;
        mEncodingHeight = height;
        mBitRate = bitRate;
    }

    /**********************************************************
     * capture video frame one by one from the preview window
     * setup the buffer to hold the images
     **********************************************************/
    private synchronized  void setupVideoFrameCallback() {
        if (mCamera == null) {
            Log.e(TAG, "Camera object is null in setupVideoFrameCallback!");
            return;
        }
        mFrameCatcher = new FrameCatcher( mPreviewWidth,  mPreviewHeight);
        long bufferSize = 0;
        bufferSize = mPreviewWidth * mPreviewHeight  * ImageFormat.getBitsPerPixel(mImageFormat) / 8;
        long sizeWeShouldHave = (mPreviewWidth * 	mPreviewHeight  * 3 / 2);
        Log.d(TAG, "BufferSize for videodata is: " +   bufferSize );
        Log.d(TAG, "Buffer size we should have is: " +  sizeWeShouldHave  );
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.setPreviewCallbackWithBuffer( mFrameCatcher );
        for (int i = 0; i < NUM_CAMERA_PREVIEW_BUFFERS; i++) {
            byte [] cameraBuffer = new byte[(int)bufferSize];
            mCamera.addCallbackBuffer(cameraBuffer);
        }
    }

    /******************************************************************************************
     *  The preview window can supprt different image formats depending on the camera make
     *  Almost all support NV21 and JPEG
     * @param parameters
     ****************************************************************************************/
    private synchronized  void  queryPreviewSettings(Camera.Parameters parameters) {
        List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();
        for (int[] item : supportedFps) {
            Log.d(TAG, "Mix preview frame rate supported: " + item[ Camera.Parameters.PREVIEW_FPS_MIN_INDEX]/ 1000  );
            Log.d(TAG, "Max preview frame rate supported: " + item[ Camera.Parameters.PREVIEW_FPS_MAX_INDEX]/ 1000  );
        }
        List<Integer> formats = parameters.getSupportedPreviewFormats();
        for (Integer format : formats) {
            if (format == ImageFormat.JPEG)  {
                Log.d(TAG, "This camera supports JPEG format in preview");
            }
            if (format == ImageFormat.NV16)  {
                Log.d(TAG, "This camera supports NV16 format in preview");
            }
            if (format == ImageFormat.NV21)  {
                Log.d(TAG, "This camera supports NV21 format in preview");
            }
            if (format == ImageFormat.RGB_565)  {
                Log.d(TAG, "This camera supports RGB_5645 format in preview");
            }
            if (format == ImageFormat.YUV_420_888)  {
                Log.d(TAG, "This camera supports YUV_420_888 format in preview");
            }
            if (format == ImageFormat.YUY2)  {
                Log.d(TAG, "This camera supports YUY2 format in preview");
            }
            if (format == ImageFormat.YV12)  {
                Log.d(TAG, "This camera supports YV12 format in preview");
            }
            if (format == ImageFormat.UNKNOWN)  {
                Log.e(TAG, "This camera supports UNKNOWN format in preview");
            }
        }

        mImageFormat = parameters.getPreviewFormat();
        if (mImageFormat != ImageFormat.NV21) {
            Log.e(TAG,  "Bad reported image format, wanted NV21 (" + ImageFormat.NV21 +
                    ") got " + mImageFormat);
        }
    }

    /*******************************************************************
     * Change this to the resolution you want to capture and encode to
     * @param parameters camera preview settings
     ******************************************************************/
    private synchronized void adjustPreviewSize(Camera.Parameters parameters) {
        mPreviewWidth = parameters.getPreviewSize().width;
        mPreviewHeight = parameters.getPictureSize().height;
        Log.d(TAG, "Current preview width and height is: " + mPreviewWidth  + "," + mPreviewHeight);
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            Log.d(TAG , "Preview sizes supported by this camera is: " + size.width + "x" + size.height);
        }
        Camera.Size bestSize = getBestPreviewSize(sizes,  (int)mPreviewWidth, (int)mPreviewHeight);
        mPreviewWidth   = bestSize.width;
        mPreviewHeight =  bestSize.height;

        // if its more than Encoding width and height adjust it to the same
        if (mPreviewWidth > mEncodingWidth) {
           mPreviewWidth = mEncodingWidth;
        }
        if (mPreviewHeight > mEncodingHeight) {
           mPreviewHeight = mEncodingHeight;
        }

        Log.d(TAG, "New preview size is: " +  mPreviewWidth  + "x" + mPreviewHeight );
        parameters.setPreviewSize( (int)mPreviewWidth,  (int)mPreviewHeight);
    }

    /*****************************************************************************************************
     * Make sure the preview capture rate is consistent by locking the exposure and white balance rate
     * @param parameters
     * @param frameRate
     ****************************************************************************************************/
    private synchronized void setPreviewFrameRate(Camera.Parameters parameters, int frameRate) {
        int actualMin = frameRate * 1000;
        int actualMax = actualMin;
        // try to lock the camera settings to get the frame rate we want
        if (parameters.isAutoExposureLockSupported()) {
            parameters.setAutoExposureLock(true);
        }
        if (parameters.isAutoWhiteBalanceLockSupported()) {
            parameters.setAutoWhiteBalanceLock(true);
        }
        Log.d(TAG, "Setting PreviewWindow frame rate to: " + String.valueOf( actualMin) + ":" + String.valueOf(actualMax));
        parameters.setPreviewFpsRange( actualMin, actualMax ); // for 30 fps
    }

    public synchronized float getPreviewSizeWidth() {
        return mPreviewWidth;
    }
    public synchronized  float getPreviewSizeHeight() {
        return mPreviewHeight;
    }

    /*****************************************************************
     * getSharedMemFile
     * @return MemoryFile  which contain all captured video frames
     ****************************************************************/
    public synchronized SharedVideoMemory getSharedMemFile() {
        return mFrameCatcher.getSharedMemFile();
    }


    public synchronized void setRecordingState(Boolean state) {
        mFrameCatcher.setRecordingState(state);
    }

     public Camera.Parameters getParameters() {
        return mCamera.getParameters();
     }
}
