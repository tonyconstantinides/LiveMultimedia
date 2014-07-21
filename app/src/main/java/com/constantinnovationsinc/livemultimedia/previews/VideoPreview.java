package com.constantinnovationsinc.livemultimedia.previews;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * This class provides the camera app preview that Android requires if you going to operate the hardware camera
 * This preview serves two purposes. Firstly it allows the user to see what the camera sees and it also allows
 * the app to capture video frame from the preview window.
 */
public class VideoPreview extends SurfaceView {

    SurfaceHolder mHolder;
    private Camera mCamera = null;
    List<Size> mSupportedPreviewSizes;
    Size mPreviewSize;

    public VideoPreview(Context context) {
        super(context);
        mHolder = getHolder();
    }

    /** the Camera associated with this surfaces is typically passed in
     *
     * @param camera - Active camera, can be front or back
     */
    public void setCamera(Camera camera) {
        mCamera = camera;
    }


    /** As the surface is being created the camera preview size can be set
     * This may be called multiple times during the app as the user starts and stops the camera
     * Each time a new surface may be created and a new preview window set
    */
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            Camera.Size size = getBestPreviewSize(mCamera.getParameters().getSupportedPreviewSizes(),
                    getMeasuredWidth(), getMeasuredHeight());
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(size.width, size.height);
            mCamera.setParameters(parameters);

            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("HSCPreview", "Error setting camera preview: " + e.getMessage());
        }
    }

    /**
     *
     * @param holder
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d("HSCPreview", "Error starting camera preview: " + e.getMessage());
        }
    }

    public static Camera.Size getBestPreviewSize(List<Camera.Size> previewSizes, int width, int height) {
        Camera.Size result = null;

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

}

