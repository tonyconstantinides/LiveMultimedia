package com.constantinnovationsinc.livemultimedia.cameras;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Created by constantinnovationsinc on 6/10/15.
 */
public class LollipopCamera  extends AndroidCamera {
   private static final String TAG = JellyBeanCamera.class.getCanonicalName();
   private CameraManager mCameraManager = null;

   public void onFrameAvailable( @NonNull SurfaceTexture surfaceTexture) {
            Log.w(TAG, "Receiving frames from Texture!");
   }

   public  LollipopCamera() {
    }

   public LollipopCamera( @NonNull Context context) {
        super(context);
        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
   }
}
