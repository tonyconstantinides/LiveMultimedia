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
package com.constantinnovationsinc.livemultimedia.threads;
import android.graphics.SurfaceTexture;
import android.os.HandlerThread;
import android.util.Log;
import com.constantinnovationsinc.livemultimedia.cameras.JellyBeanCamera;
import java.io.IOException;

public class CameraThread extends HandlerThread {
    private static final String TAG = CameraThread.class.getCanonicalName();
    private JellyBeanCamera mCamera = null;
    private SurfaceTexture mTexture = null;
    private static final int NO_CAMERA = 0;
    private static final int ONE_CAMERA = 1;
    private static final int TWO_CAMERA = 2;
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

    @SuppressWarnings("deprecation")
    @Override
    protected void onLooperPrepared() {
        Log.d(TAG, "onLooperPrepared!");
        try {
            if (mCamera == null) {
                throw new IllegalStateException("Camera object is null, leaving...");
            }
            int camCount = mCamera.getNumberOfCameras();
            Log.d(TAG, "Number of Camera reported: " + camCount);

            if (camCount == NO_CAMERA || camCount < NO_CAMERA) {
                Log.e(TAG, "No Cameras found, exiting!");
                throw new  IllegalStateException("Unable to open camera");
            } else  if (camCount  == ONE_CAMERA && mActiveCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera.startFrontCamera();
            } else if (camCount == ONE_CAMERA && mActiveCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCamera.startFrontCamera();
            } else if (camCount == TWO_CAMERA && mActiveCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera.startFrontCamera();
            } else if (camCount == TWO_CAMERA && mActiveCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCamera.startBackCamera();
            } else {
                Log.e(TAG, "Serious error, active cam not set!");
                Thread.currentThread().interrupt();
                return;
            }
            Log.d(TAG, "Settingup the Preview Window");
            mCamera.setupPreviewWindow();
            mCamera.setupVideoCaptureMethod();
            mCamera.setPreviewTexture(mTexture);
            Log.d(TAG, "Starting the camera preview..");
            mCamera.startVideoPreview();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "--------------------------------------------");
            Log.e(TAG, "Error when starting PreviewWindow!!");
            Log.e(TAG, e.toString());
            Log.e(TAG, "--------------------------------------------");
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "CameraThread Running!");
        super.run();
    }
}
