package com.constantinnovationsinc.livemultimedia.cameras;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;

public class AndroidCamera implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = AndroidCamera.class.getCanonicalName();
    protected static final String NULL_IN_START_FRONT_CAMERA   = "Camera is Null in startFrontCamera()";
    protected static final String NULL_IN_START_BACK_CAMERA    = "Camera is Null in startBackCamera()";
    protected static final String NULL_IN_GET_PARAMETERS       = "Camera object is Null in getParameters()";
    protected static final String NULL_IN_GET_SHARED_MEM_FILE  = "FramesReadyCallback is Null in getSharedMemFile()";
    protected static final String NULL_IN_SET_RECORDING_STATE  = "FramesReadyCallback is Null in setRecordingState()";
    protected static final String NULL_IN_GET_RECORDING_STATE  = "FramesReadyCallback is Null in getRecordingState()";
    protected static final String NULL_IN_SET_RECORD_HINT      = "Camera.Parameters is Null in SetRecordHint()";
    protected static final String ARGUMENT_NULL_IN_SET_ONFRAMES_READY_CALLBACK = "Passing  Null for a callback in setOnFramesReadyCallBack()";
    protected static final String NULL_IN_SET_ONFRAMES_READY_CALLBACK = "FramesReadyCallback is Null in setOnFramesReadyCallBack()";
    protected  int mBitRate  = -1;
    protected  int mEncodingWidth = -1;
    protected  int mEncodingHeight = -1;
    protected  long mPreviewWidth = -1;
    protected  long mPreviewHeight = -1;
    protected  int mImageFormat = -1;
    private  Context mContext = null;

    public AndroidCamera() {
        Log.w(TAG, "Android Camera constructor!");
    }

    public AndroidCamera(Context context) {
        mContext = context;
    }

    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.w(TAG, "Receiving frames from Texture!");
   }

   protected Context getContext() {
        return mContext;
   }
}