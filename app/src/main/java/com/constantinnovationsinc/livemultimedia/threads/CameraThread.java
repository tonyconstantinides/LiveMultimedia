package com.constantinnovationsinc.livemultimedia.threads;

import android.graphics.SurfaceTexture;
import android.os.HandlerThread;
import android.util.Log;

import com.constantinnovationsinc.livemultimedia.cameras.JellyBeanCamera;

import java.io.IOException;


public class CameraThread extends HandlerThread {
    private static final String TAG = CameraThread.class.getSimpleName();
    private JellyBeanCamera mCamera = null;
    private SurfaceTexture mTexture = null;
    public int mActiveCameraId = -1;

    public CameraThread(String name) {
        super(name);
    }

    public synchronized void setCamera(JellyBeanCamera camera) {
        mCamera = camera;
    }

    public synchronized void setCameraTexture(SurfaceTexture texture) {
        mTexture = texture;
    }

    public synchronized void setActiveCameraId(int cameraId) {
        mActiveCameraId = cameraId;
    }

    @Override
    public void run() {
        super.run();
        Log.d(TAG, "CameraThread Running!");
        try {
            if (mCamera == null) {
                throw new IllegalStateException("Camera object is null, leaving...");
            }
            int camCount = mCamera.getNumberOfCameras();
            if (camCount == 0) {
                Log.e(TAG, "No Cameras found, exiting!");
                throw new  IllegalStateException("Unable to open camera");
            }
            if (mActiveCameraId != -1) {
                mCamera.setActiveCameraId(mActiveCameraId);
            }
            if (mActiveCameraId == 0) {
                mCamera.startBackCamera();
            } else if (mActiveCameraId == 1) {
                mCamera.startFrontCamera();
            } else {
                Log.e(TAG, "Serious error, active cam not set!");
                Thread.currentThread().interrupt();
            }
            mCamera.setupPreviewWindow();
            mCamera.setupVideoCaptureMethod();
            mCamera.setPreviewTexture(mTexture);
            Log.d(TAG, "Starting the camera preview..");
            mCamera.startVideoPreview();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.toString());
        } catch (IllegalStateException e) {
            Log.e(TAG, e.toString());
        }
    }
}
