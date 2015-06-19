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
package com.constantinnovationsinc.livemultimedia.cameras;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import com.constantinnovationsinc.livemultimedia.callbacks.FramesReadyCallback;
import com.constantinnovationsinc.livemultimedia.callbacks.FrameCatcher;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;
import java.io.IOException;
import java.util.List;

/****************************************************************************
 * The Class wraps around the Pre-Lollipop API for Android Devices
 * Note I soon be adding a LollipopCamera classes for Camera2 API
 * JellyBeanCamera works from Android version JellyBean and up to KitKat.
 * Android 4.3 and 4.4
 **************************************************************************/
public class JellyBeanCamera extends AndroidCamera {
    private static final String TAG = JellyBeanCamera.class.getCanonicalName();

    private static final int ENCODING_WIDTH  = 1280;
    private static final int ENCODING_HEIGHT = 720;
    private static final int BITRATE = 6000000;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    // movie length, in frames
    private static int mActiveCameraId = -1;
    private int mFrameRate = 30;
    public  Boolean  mYV12ColorFormatSupported = false;
    public Boolean   mNV21ColorFormatSupported = false;
    @SuppressWarnings("deprecation")
    public  Camera mCamera = null;
    public  VideoPreview mVideoPreview = null;
    public FrameCatcher mFrameCatcher = null;


    /*********************************************************************
     * Constructor
     * @param context - the context associated with this encoding thread
     *********************************************************************/
    public JellyBeanCamera(Context context, VideoPreview videoPreview) {
        super(context);
        Log.d(TAG, "JellyBean constructor called!");
        mVideoPreview = videoPreview;
    }

    /*********************************************************************
     * startBackCamera
     * @return camera - active camera
     *********************************************************************/
    @SuppressWarnings({"deprecation", "unused"})
    public synchronized Camera startBackCamera() throws IllegalStateException{
        Log.d(TAG, "startBackCamera()");
        setParameters(ENCODING_WIDTH, ENCODING_HEIGHT, BITRATE);
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        setActiveCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
        return mCamera;
    }

    /*********************************************************************
     * startFrontCamera
     * @return camera - active camera
     *********************************************************************/
    @SuppressWarnings({"deprecation", "unused"})
    public synchronized Camera startFrontCamera() throws IllegalStateException{
        Log.d(TAG, "startFrontCamera()");
        setParameters(ENCODING_WIDTH, ENCODING_HEIGHT, BITRATE);
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        setActiveCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
        return mCamera;
    }

    /*********************************************************************
     * getNumberOfCameras()
     * @return int - number of cameras on the device
     *********************************************************************/
    @SuppressWarnings("deprecation")
    public synchronized  int getNumberOfCameras() {
        // Try to find a front-facing camera (e.g. for videoconferencing).
        return  Camera.getNumberOfCameras();
    }

    /*********************************************************************
     * isBackCamera()
     * @return Boolean - Is the active selected camera the back camera
     *********************************************************************/
    @SuppressWarnings("deprecation")
    public synchronized  Boolean isBackCamera() {
        Boolean flag = false;
        if (getActiveCameraId() == -1)
            return false;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(getActiveCameraId(), info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
           flag = true;
        }
        return flag;
    }

    /*********************************************************************
     * isFrontCamera()
     * @return Boolean - Is the active selected camera the front camera
     *********************************************************************/
    @SuppressWarnings("deprecation")
    public synchronized  Boolean isFrontCamera() {
        Boolean flag = false;
        if (getActiveCameraId() == -1)
            return false;
        Camera.CameraInfo info =  new Camera.CameraInfo();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            flag = true;
        }
        return flag;
    }

    /**********************************************************
     *  stopCamera - Stops the preview and the capture process
     **********************************************************/
    public synchronized void  stopCamera() {
        Log.d(TAG, "stopCamera()!");
        if (mCamera == null) {
            Log.e(TAG, "stopCamera() failed due to null camera");
            throw new IllegalStateException("Camera object is Null in stopCamera");
        }
        mCamera.stopPreview();
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.release();
        mCamera = null;
    }

    /*****************************************************************
     *  isNV21ColorFormat - IS the format NV21
     *  @return Boolean - Is it NV21 Colorformat in the preview window
     *****************************************************************/
    public synchronized Boolean isNV21ColorFormat() {
       return mNV21ColorFormatSupported;
    }

    /*****************************************************************
     *  isYV12ColorFormat - Is the format NV21
     *  @return Boolean - Is it YV12 Colorformat in the preview window
     *****************************************************************/
    public synchronized Boolean isYV12ColorFormat() {
        return mYV12ColorFormatSupported;
    }

    /*****************************************************************
     *  getImageFormat - What is the the imageFormat
     *  @return String - Get Image Format
     *****************************************************************/
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
    public synchronized void setupPreviewWindow() throws IllegalStateException{
        Log.d(TAG, "Camera setup the preview Window");
        try {
            if (mCamera == null) {
                Log.e(TAG, "setupPreviewWindiow() failed due to null camera");
                throw new IllegalStateException("Camera object is Null in setupPreviewWindow");
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
            setPreviewFrameRate(parameters, mFrameRate);
            mCamera.setParameters(parameters);
        } catch (IllegalArgumentException  | IllegalStateException e) {
           Log.e(TAG,  e.toString());
        }
    }

    /**********************************************************
     * setupVideoCaptureMethod  encapsulates the current video
     * frame capture method
     **********************************************************/
    public synchronized void setupVideoCaptureMethod() {
        // set up the callback to capture the video frames
        Log.d(TAG, "setupVideoCaptureMethod()");
        setupVideoFrameCallback();
    }

    /**********************************************************
     * setPreviewTexture same as the camera pi but with error handling
     * @param surface surfaceTexture of the preview window
     **********************************************************/
    public  synchronized void setPreviewTexture( SurfaceTexture surface) throws IllegalArgumentException, IllegalStateException, IOException {
        if (surface == null) {
            throw new IllegalArgumentException("SurfaceTexture is Null in setPreviewTexture()");
        }
        if (mCamera == null) {
            throw new IllegalStateException("Camera object is Null in  setPreviewTexture()");
        }
        // this call throw an IOException
        mCamera.setPreviewTexture(surface);
    }

    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.w(TAG, "Receiving frames from Texture!");
    }

    /**********************************************************
     * startVideoPreview( start the preview so you cna begin capturing frames
     * @return the preview started or not
     **********************************************************/
    @SuppressWarnings("all")
    public synchronized Boolean startVideoPreview() throws  IllegalStateException{
        Log.d(TAG, "startVideoPreview()");
        if (mCamera == null ) {
            throw new IllegalStateException("Null Camera object in startVideoPreview");
        }
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(ENCODING_WIDTH, ENCODING_HEIGHT);
        mCamera.setParameters(parameters);
        mCamera.startPreview();
       return true;
    }

    /*******************************************************************
     * release stops the preview and the video capture and then safety
     *  releases the camera
     ******************************************************************/
    public synchronized void release() {
        Log.d(TAG, "release() called on the Camera class");
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

    /********************************************************************
     * adjustCameraBasedOnOrientation() ensure the view is in landscape
     *******************************************************************/
    public synchronized void adjustCameraBasedOnOrientation() {
        if (getContext() == null)
            return;
        if (getContext().getResources() == null)
            return;
        if (getContext().getResources().getConfiguration() == null)
            return;
        if (mCamera == null)
            throw new IllegalStateException("Camera object is Null in adjustCameraBasedOnOrientation");

         if (getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
               mCamera.setDisplayOrientation(270);
         }
    }

    /************************************************************
     * ensure the view is in landscape
     ************************************************************/
    @SuppressWarnings("deprecation")
    private synchronized static Camera.Size getBestPreviewSize(List<Camera.Size> previewSizes, int width, int height) {
        Camera.Size result = null;
        if (width == 0 || height == 0) {
            Log.e(TAG, "Width or Height of preview surface is zero!");
            return  null;
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
     * @param width width
     * @param height height
     * @param bitRate the bitRate
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
        Log.d(TAG, "setupVideoFrameCallback(() called on the Camera class");
        if (mCamera == null) {
            Log.e(TAG, "Camera object is null in setupVideoFrameCallback!");
            return;
        }
        mFrameCatcher = new FrameCatcher( mPreviewWidth,
                                          mPreviewHeight,
                                          getImageFormat(),
                                          mVideoPreview);
        long bufferSize;
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
     * @param parameters preview window parms
     ****************************************************************************************/
    @SuppressWarnings("deprecation")
    private synchronized  void  queryPreviewSettings(Camera.Parameters parameters) {
        List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();
        for (int[] item : supportedFps) {
            Log.d(TAG, "Mix preview frame rate supported: " + item[ Camera.Parameters.PREVIEW_FPS_MIN_INDEX]/ 1000  );
            Log.d(TAG, "Max preview frame rate supported: " + item[ Camera.Parameters.PREVIEW_FPS_MAX_INDEX]/ 1000  );
        }
        List<Integer> formats = parameters.getSupportedPreviewFormats();
        for (Integer format : formats) {
            if (format == null) {
                Log.e(TAG, "This camera supports illegal format in preview");
                break;
            }
            switch ( format.intValue() ) {
                case ImageFormat.JPEG:
                    Log.d(TAG, "This camera supports JPEG format in preview");
                    break;
                case ImageFormat.NV16:
                    Log.d(TAG, "This camera supports NV16 format in preview");
                    break;
                case ImageFormat.NV21:
                    Log.d(TAG, "This camera supports NV21 format in preview");
                    mNV21ColorFormatSupported = true;
                    break;
                case ImageFormat.RGB_565:
                    Log.d(TAG, "This camera supports RGB_5645 format in preview");
                    break;
                case ImageFormat.YUV_420_888:
                    Log.d(TAG, "This camera supports YUV_420_888 format in preview");
                    break;
                case ImageFormat.YUY2:
                    Log.d(TAG, "This camera supports YUY2 format in preview");
                    break;
                case ImageFormat.YV12:
                     Log.d(TAG, "This camera supports YV12 format in preview");
                    mYV12ColorFormatSupported = true;
                    break;
                case ImageFormat.UNKNOWN:
                     Log.e(TAG, "This camera supports UNKNOWN format in preview");
                    break;
                default:
                    Log.e(TAG, "This camera supports illegal format in preview");
                    break;
            }
        }
    }

    /*******************************************************************
     * Change this to the resolution you want to capture and encode to
     * @param parameters camera preview settings
     ******************************************************************/
    @SuppressWarnings("deprecation")
    private synchronized void adjustPreviewSize(Camera.Parameters parameters) {
        Log.d(TAG, "adjustPreviewSize(Camera.Parameters parameters)");
        mPreviewWidth = parameters.getPreviewSize().width;
        mPreviewHeight = parameters.getPictureSize().height;
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            Log.d(TAG , "Preview sizes supported by this camera is: " + size.width + "x" + size.height);
        }
        Camera.Size bestSize = getBestPreviewSize(sizes, (int)mPreviewWidth, (int)mPreviewHeight);
        if (bestSize != null){
            mPreviewWidth = bestSize.width;
            mPreviewHeight = bestSize.height;
            Log.w(TAG, "Recommend size of the preview window is: " + mPreviewWidth + "," + mPreviewHeight);
        } else {
            //force it to the hardcoded value
            mPreviewWidth = mEncodingWidth;
            mPreviewHeight = mEncodingHeight;
            Log.d(TAG, "New preview size is: " + mPreviewWidth + "x" + mPreviewHeight);
        }
        parameters.setPreviewSize((int) mPreviewWidth, (int) mPreviewHeight);
    }

    @SuppressWarnings("deprecation")
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
     * @param parameters camera paramaters
     * @param frameRate  teh frameRate
     ****************************************************************************************************/
    @SuppressWarnings("deprecation")
    public synchronized void setPreviewFrameRate(Camera.Parameters parameters, int frameRate) {
      int actualMin = frameRate * 1000;
      List<int[]> range =  parameters.getSupportedPreviewFpsRange();

      parameters.setPreviewFpsRange(actualMin, actualMin); // for 30 fps
    }

    /*****************************************************************************************************
     *  getPreviewSizeWidth() the preview window width size
     * @return  float - widthe
     ****************************************************************************************************/
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
    public synchronized SharedVideoMemory getSharedMemFile() throws IllegalStateException {
        if (mFrameCatcher == null) {
            throw new IllegalStateException( NULL_IN_GET_SHARED_MEM_FILE );
        }
        SharedVideoMemory shared = null;
        if (mFrameCatcher.mRecording && mFrameCatcher.isSavingVideoFrames()) {
            shared = mFrameCatcher.getSharedMemFile();
        }
        return shared;
    }

    public synchronized void setRecordingState(Boolean state) {
        if (mFrameCatcher == null) {
            throw new IllegalStateException( NULL_IN_SET_RECORDING_STATE );
        }
         mFrameCatcher.setRecordingState(state);
    }

    public synchronized Boolean getRecordingState() throws IllegalStateException {
       if (mFrameCatcher == null) {
           throw new IllegalStateException( NULL_IN_GET_RECORDING_STATE );
        }
        return  mFrameCatcher.getRecordingState();
    }

    @SuppressWarnings("deprecation")
     public synchronized  Camera.Parameters getParameters() throws IllegalStateException{
        if (mCamera == null) {
            throw new IllegalStateException( NULL_IN_GET_PARAMETERS );
        }
        return mCamera.getParameters();
     }

     public synchronized  int getActiveCameraId() {
        return mActiveCameraId;
     }

    @SuppressWarnings("deprecation")
     public synchronized Camera getRawCamera() {
        return mCamera;
     }

    public synchronized  int getBitRate() {
        return mBitRate;
    }

    @SuppressWarnings("deprecation")
     public synchronized void setActiveCameraId(int activeCam) {
        if (activeCam == Camera.CameraInfo.CAMERA_FACING_BACK ||
            activeCam == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mActiveCameraId = activeCam;
        }  else {
            Log.e(TAG, "Unknown Camera Id");
        }
     }

    /*****************************************************************
     * setRecordingHint  just sets the recording hint and check for null
     * @param value true or false
     * @param parms the camera parms
     * @throws IllegalArgumentException if camera parms is null
     ******************************************************************/
    @SuppressWarnings("deprecation")
    public synchronized void setRecordingHint(Boolean value, Camera.Parameters parms) throws IllegalArgumentException {
       if (parms == null) {
           throw new IllegalArgumentException( NULL_IN_SET_RECORD_HINT );
       }
       parms.setRecordingHint(value);
    }

    /*****************************************************************
    * setOnFramesReadyCallback just sets up the preview window callback
    * implementation approach for video frame capture.
    * @param  callback  - the actual callback function
    * @throws IllegalArgumentException, IllegalStateException
    ******************************************************************/
     public synchronized  void setOnFramesReadyCallBack(FramesReadyCallback callback) throws IllegalArgumentException, IllegalStateException {
        if (callback == null) {
            throw new IllegalArgumentException( ARGUMENT_NULL_IN_SET_ONFRAMES_READY_CALLBACK );
        }
        if (mFrameCatcher == null) {
            throw new IllegalStateException(  NULL_IN_SET_ONFRAMES_READY_CALLBACK );
        }
        mFrameCatcher.callback = callback;
     }
}