package com.constantinnovationsinc.livemultimedia.cameras;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import java.io.IOException;
import java.util.List;

import com.constantinnovationsinc.livemultimedia.callbacks.FramesReadyCallback;
import com.constantinnovationsinc.livemultimedia.callbacks.FrameCatcher;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;

/**
 * Created by constantinnovationsinc on 8/6/14.
 */
public class JellyBeanCamera   implements SurfaceTexture.OnFrameAvailableListener{
    private static final String TAG = JellyBeanCamera.class.getName();
    private int mBitRate  = -1;
    private int mEncodingWidth = -1;
    private int mEncodingHeight = -1;
    private long mPreviewWidth = -1;
    private long mPreviewHeight = -1;
    private int mImageFormat = -1;
    private Context mContext = null;

    private static final int ENCODING_WIDTH  = 1280;
    private static final int ENCODING_HEIGHT = 720;
    private static final int BITRATE = 6000000;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    // movie length, in frames
    private static final int NUM_FRAMES = 300;               // 9 seconds of video
    private static int FRAME_RATE = 30;
    private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/media/";
    private static final String  MIME_TYPE = "video/avc";
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static int mActiveCameraId = -1;
    public  Boolean  mYV12ColorFormatSupported = false;
    public Boolean   mNV21ColorFormatSupported = false;
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
            setActiveCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
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
            setActiveCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return mCamera;
    }

    public synchronized  int getNumberOfCameras() {
        // Try to find a front-facing camera (e.g. for videoconferencing).
        return  Camera.getNumberOfCameras();
    }

    public synchronized  Boolean isBackCamera() {
        Boolean flag = false;
        if (getActiveCameraId() == -1)
            return false;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo( getActiveCameraId(), info);
        if (info.facing == info.CAMERA_FACING_BACK) {
           flag = true;
        }
        return flag;
    }

    public synchronized  Boolean isFrontCamera() {
        Boolean flag = false;
        if (getActiveCameraId() == -1)
            return false;
        Camera.CameraInfo info =  new Camera.CameraInfo();
        if (info.facing == info.CAMERA_FACING_FRONT) {
            flag = true;
        }
        return flag;
    }

    /**********************************************************
     *  stopCamera - 
     **********************************************************/
    public synchronized void  stopCamera() {
        Log.d(TAG, "Camera preview stopped and Camera released!");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
        }
        mCamera = null;
    }

    public synchronized Boolean isNV21ColorFormat() {
       return mNV21ColorFormatSupported;
    }

    public synchronized Boolean isYV12ColorFormat() {
        return mYV12ColorFormatSupported;
    }

    public synchronized String getImageFormat() {
        String value = null;
        if (mYV12ColorFormatSupported && mNV21ColorFormatSupported)  {
            value = "YV12";
        } else {
            value = "UNKNOWN";
        }
        return value;
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
        adjustCameraBasedOnOrientation();
        setRecordingHint(true, parameters);
        // set the preview size
        queryPreviewSettings(parameters);
        if (mYV12ColorFormatSupported) {
            mImageFormat = ImageFormat.YV12;
            parameters.setPreviewFormat(ImageFormat.YV12);
        } else   if (mNV21ColorFormatSupported) {
            mImageFormat = ImageFormat.NV21;
            parameters.setPreviewFormat(ImageFormat.NV21);
         }
        adjustPreviewSize(parameters);
        // lock the exposure ans white balance to get a constant preview frame rate
        lockExposureAndWhiteBalance(parameters);
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

    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.w(TAG, "Receiving frames from Textur!e");
    }

    /**********************************************************
     * startVideoPreview( start the preview so you cna begin capturing frames
     * @return the preview started or not
     **********************************************************/
    public synchronized Boolean startVideoPreview() {
        try {
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters != null) {
                    parameters.setPreviewSize(ENCODING_WIDTH, ENCODING_HEIGHT);
                    mCamera.setParameters(parameters);
                    mCamera.startPreview();
                }
             }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return true;
    }

    /*******************************************************************
     * release stops the preview and the video capture and then safety
     *  releases the camera
     ******************************************************************/
    public synchronized void release() {
        if (mCamera != null) {
            mCamera.addCallbackBuffer(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        if (mFrameCatcher != null) {
            mFrameCatcher.setRecordingState(false);
            getSharedMemFile().clearMemory();
            mFrameCatcher = null;
        }
    }

    /************************************************************
     * adjustCameraBasedOnOrientation() ensure the view is in landscape
     ************************************************************/
    public synchronized void adjustCameraBasedOnOrientation() {
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mCamera.setDisplayOrientation(270);
        }
    }

    /************************************************************
     * ensure the view is in landscape
     ************************************************************/
    private synchronized static Camera.Size getBestPreviewSize(List<Camera.Size> previewSizes, int width, int height) {
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
        mFrameCatcher = new FrameCatcher( mPreviewWidth,
                                          mPreviewHeight,
                                          getImageFormat(),
                                          (FramesReadyCallback)mVideoPreview);
        long bufferSize = 0;
        bufferSize = mPreviewWidth * mPreviewHeight  * ImageFormat.getBitsPerPixel(mImageFormat) / 8;
        long sizeWeShouldHave = (mPreviewWidth * 	mPreviewHeight  * 3 / 2);
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
                mNV21ColorFormatSupported = true;
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
                mYV12ColorFormatSupported = true;
            }
            if (format == ImageFormat.UNKNOWN)  {
                Log.e(TAG, "This camera supports UNKNOWN format in preview");
            }
        }

    }

    /*******************************************************************
     * Change this to the resolution you want to capture and encode to
     * @param parameters camera preview settings
     ******************************************************************/
    private synchronized void adjustPreviewSize(Camera.Parameters parameters) {
        mPreviewWidth = parameters.getPreviewSize().width;
        mPreviewHeight = parameters.getPictureSize().height;
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            Log.d(TAG , "Preview sizes supported by this camera is: " + size.width + "x" + size.height);
        }
        Camera.Size bestSize = getBestPreviewSize(sizes,  (int)mPreviewWidth, (int)mPreviewHeight);
        mPreviewWidth   = bestSize.width;
        mPreviewHeight =  bestSize.height;
        Log.w(TAG, "Recommend size of the preview window is: " + mPreviewWidth + "," + mPreviewHeight);
        //force it to the hardcoded value
        mPreviewWidth = mEncodingWidth;
        mPreviewHeight = mEncodingHeight;
        Log.d(TAG, "New preview size is: " +  mPreviewWidth  + "x" + mPreviewHeight );
        parameters.setPreviewSize( (int)mPreviewWidth,  (int)mPreviewHeight);
    }



    public synchronized void lockExposureAndWhiteBalance(Camera.Parameters parameters) {
        // try to lock the camera settings to get the frame rate we want
        if (parameters.isAutoExposureLockSupported()) {
            parameters.setAutoExposureLock(true);
        }
        if (parameters.isAutoWhiteBalanceLockSupported()) {
            parameters.setAutoWhiteBalanceLock(true);
        }
    }

    /*****************************************************************************************************
     * Make sure the preview capture rate is consistent by locking the exposure and white balance rate
     * @param parameters
     * @param frameRate
     ****************************************************************************************************/
    public synchronized void setPreviewFrameRate(Camera.Parameters parameters, int frameRate) {
        int actualMin = frameRate * 1000;
        int actualMax = actualMin;

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
        SharedVideoMemory shared = null;
        if (mFrameCatcher != null && mFrameCatcher.mRecording == true && mFrameCatcher.isSavingVideoFrames()) {
            shared = mFrameCatcher.getSharedMemFile();
        }
        return shared;
    }

    public synchronized void setRecordingState(Boolean state) {
        if (mFrameCatcher != null) {
            mFrameCatcher.setRecordingState(state);
        }
    }

    public synchronized Boolean getRecordingState() {
       Boolean isRecording = false;
        if (mFrameCatcher != null) {
            isRecording = mFrameCatcher.getRecordingState();
        }
        return isRecording;
    }

     public synchronized  Camera.Parameters getParameters() {
        Camera.Parameters parms = null;
        if (mCamera != null) {
            parms = mCamera.getParameters();
        }
        return parms;
     }

     public synchronized  int getActiveCameraId() {
        return mActiveCameraId;
     }

     public synchronized Camera getRawCamera() {
        return mCamera;
     }

     public synchronized void setActiveCameraId(int activeCam) {
        if (activeCam == Camera.CameraInfo.CAMERA_FACING_BACK ||
            activeCam == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mActiveCameraId = activeCam;
        }  else {
            Log.e(TAG, "Unknown Camera Id");
        }
     }

    public synchronized void setRecordingHint(Boolean value, Camera.Parameters parms) {
            parms.setRecordingHint(value);
    }

     public synchronized  void setOnFramesReadyCallBack(FramesReadyCallback callback) {
        if (mFrameCatcher != null) {
            mFrameCatcher.callback = callback;
        }
     }
}
