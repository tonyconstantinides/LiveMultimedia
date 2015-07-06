package com.constantinnovationsinc.livemultimedia.cameras;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.exceptions.CameraException;
import com.constantinnovationsinc.livemultimedia.views.CameraView;

public class AndroidCamera  {
    public   static final int PHOTO = 1;
    public   static final int VIDEO = 2;
    public   static final int BOTH = 3;
    private static final String TAG = AndroidCamera.class.getCanonicalName();
    private  boolean  mCameraSetup = false;
    private  boolean  mCameraOpen = false;
    private  Context mContext = null;
    private  BaseCamera mCamera = null;
    private  boolean mUseLegecyApi = false;
    private  boolean mUseLimitedApi = false;
    private  boolean mUseFullApi = false;
    private  int mCameraMode = 0;
    private int mCurrentApiVersion = -1;

    public AndroidCamera() {
        Log.w(TAG, "Android Camera default constructor!");
    }

    public AndroidCamera(Context context) {
        mContext = context;
    }

    public AndroidCamera(Context context, int mode) {
        mContext = context;
        mCameraMode = mode;
        setupCamera(mCameraMode, null);
    }

    public AndroidCamera(Context context, int mode, CameraView view) {
        mContext = context;
        mCameraMode = mode;
        setupCamera(mCameraMode, view);
    }

     public void setupCamera(int mode, CameraView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            useLegacyCameraApi();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            useCamera2Api(view);
        }
        mCameraMode = mode;
        if (mCameraMode == VIDEO) {
            BaseCamera.VIDEO_MODE = true;
            mCamera.setVideoPreview(view);
        } else if (mCameraMode == PHOTO) {
            BaseCamera.PHOTO_MODE = true;
        } else if (mCameraMode == BOTH) {
            BaseCamera.BOTH_MODE = true;
        }
        mCameraSetup = true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setupVideoCapture() {
        ((LollipopCamera)mCamera).setupVideoCapture();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public synchronized void openCamera(int width, int height) {
        ((LollipopCamera)mCamera).openCamera(width, height);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void configureTransform(int width, int height) {
        ((LollipopCamera)mCamera).configureTransform(width, height);
    }

    public void stopCamera() {
        Log.d(TAG, "Camera released and SurfaceTextureListener is not null!");
        if (mUseLimitedApi || mUseFullApi ) {
            ((LollipopCamera)mCamera).closeCamera();
            mCamera = null;
        }
    }

    public synchronized AndroidCamera startBackCamera() throws IllegalStateException {
        try {
          if (mUseLegecyApi) {
              mCamera = ((JellyBeanCamera) mCamera).startBackCamera();
          } else if (mUseLimitedApi || mUseFullApi ) {
              mCamera = ((LollipopCamera) mCamera).startFrontCamera();
          }
        } catch (IllegalStateException state) {
              Log.e(TAG,  state.getMessage());
        }
        return this;
    }

    public synchronized AndroidCamera startFrontCamera() throws CameraException{
       try {
            if (mUseLegecyApi) {
                mCamera = ((JellyBeanCamera) mCamera).startFrontCamera();
            } else if (mUseLimitedApi || mUseFullApi ) {
                mCamera = ((LollipopCamera) mCamera).startFrontCamera();
            }
      } catch (IllegalStateException state) {
           throw new CameraException(state.getMessage());
       }
       return this;
    }

    private void useLegacyCameraApi() {
        mCamera = new JellyBeanCamera(mContext);
        mUseLegecyApi = true;
        mUseLimitedApi = false;
        mUseFullApi = false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void useCamera2Api(CameraView view) {
        mCamera = new LollipopCamera(mContext, view);
        mUseLegecyApi = false;
        // see what mode the camera is reporting
        String support;
        support = ((LollipopCamera)mCamera).getCapabilities(BaseCamera.FRONT_CAMERA);
        if (support != null) {
            if (support.contentEquals(BaseCamera.LEGACY)) {
                Log.e(TAG, "_________________________________________________________________________");
                Log.e(TAG,"Legacy Camera api is supported but Android version is Lollipop or better!!");
                Log.e(TAG, "_________________________________________________________________________");
                mUseLegecyApi = true;
            } else if (support.contentEquals(BaseCamera.LIMITED)) {
                Log.d(TAG,"_________________________________________________________________________");
                Log.d(TAG, "LIMITED Camera api is supported for Android version is Lollipop or better!!");
                Log.d(TAG,"_________________________________________________________________________");
                mUseLimitedApi = true;
            } else if (support.contentEquals(BaseCamera.FULL)) {
                Log.d(TAG,"_________________________________________________________________________");
                Log.d(TAG, "FULL Camera api is supported for  Android version is Lollipop or better!!");
                Log.d(TAG,"_________________________________________________________________________");
                mUseFullApi = true;
            }
        }
    }

}