package com.constantinnovationsinc.livemultimedia.cameras;

import android.content.Context;
import android.util.Log;

import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.views.CameraView;

public class BaseCamera {
    private static final String TAG = BaseCamera.class.getCanonicalName();
    protected static final String NULL_IN_START_FRONT_CAMERA   = "Camera is Null in startFrontCamera()";
    protected static final String NULL_IN_START_BACK_CAMERA    = "Camera is Null in startBackCamera()";
    protected static final String NULL_IN_GET_PARAMETERS       = "Camera object is Null in getParameters()";
    protected static final String NULL_IN_GET_SHARED_MEM_FILE  = "FramesReadyCallback is Null in getSharedMemFile()";
    protected static final String NULL_IN_SET_RECORDING_STATE  = "FramesReadyCallback is Null in setRecordingState()";
    protected static final String NULL_IN_GET_RECORDING_STATE  = "FramesReadyCallback is Null in getRecordingState()";
    protected static final String NULL_IN_SET_RECORD_HINT      = "Camera.Parameters is Null in SetRecordHint()";
    protected static final String ARGUMENT_NULL_IN_SET_ONFRAMES_READY_CALLBACK = "Passing  Null for a callback in setOnFramesReadyCallBack()";
    protected static final String NULL_IN_SET_ONFRAMES_READY_CALLBACK = "FramesReadyCallback is Null in setOnFramesReadyCallBack()";
    protected static final String LEGACY  = "LEGACY";
    protected static final String LIMITED = "LIMITED";
    protected static final String FULL    = "FULL";
    protected static final int ENCODING_WIDTH  = 1280;
    protected static final int ENCODING_HEIGHT = 720;
    protected static final int BITRATE = 6000000;
    protected static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    protected  int mBitRate  = -1;
    protected  int mEncodingWidth = -1;
    protected  int mEncodingHeight = -1;
    protected  long mPreviewWidth = -1;
    protected  long mPreviewHeight = -1;
    protected  int mImageFormat = -1;
    protected  CameraView  mVideoPreview = null;
    protected  static boolean VIDEO_MODE = false;
    protected  static boolean PHOTO_MODE = false;
    protected  static boolean BOTH_MODE = false;
    private Context mContext = null;
    public static final int  BACK_CAMERA = 0;
    public static final int  FRONT_CAMERA = 1;


    public BaseCamera() {
        Log.w(TAG, "Android Camera constructor!");
    }

    public BaseCamera(Context context) {
        mContext = context;
    }

    protected Context getContext() {
        return mContext;
    }

    public void setVideoPreview(CameraView preview) {
        mVideoPreview = preview;
    }
    public CameraView getVideoPreview() {
        return mVideoPreview;
    }
}
