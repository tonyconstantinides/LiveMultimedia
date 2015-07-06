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
package com.constantinnovationsinc.livemultimedia.fragments;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.cameras.AndroidCamera;
import com.constantinnovationsinc.livemultimedia.views.CameraView;
import com.constantinnovationsinc.livemultimedia.R;


public class Camera2VideoFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = Camera2VideoFragment.class.getCanonicalName();
    private FrameLayout mVideoPreviewFrame;
    private Boolean mRecording = false;
    private CameraView mCameraView = null;      // for new api
    private VideoPreview mVideoPreview = null;  // for legacy api
    private int mCurrentApiVersion = -1;

    public static Camera2VideoFragment newInstance() {
        Camera2VideoFragment fragment = new Camera2VideoFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        destroyVideoPreviewWindow();
    }

    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView for Camera2VideoFragment");
        mCurrentApiVersion = android.os.Build.VERSION.SDK_INT;
        View view = inflater.inflate(R.layout.fragment_camera2_video, container, false);
        if (mVideoPreviewFrame != null) {
            if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
                createVideoPreviewWindow(Camera.CameraInfo.CAMERA_FACING_BACK);
            }
            if (mCurrentApiVersion >= Build.VERSION_CODES.LOLLIPOP) {
                createVideoPreviewWindowEnhanced();
            }
        }

        return view;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Button mButtonBackCamera = (Button) view.findViewById(R.id.backcamera);
        Button mButtonFrontCamera = (Button) view.findViewById(R.id.frontcamera);
        Button mButtonRecordVideo = (Button) view.findViewById(R.id.recordvideo);
        Button mButtonExit        = (Button) view.findViewById(R.id.exit);
        if (mButtonRecordVideo != null) {
            mButtonRecordVideo.setOnClickListener(this);
        }
        if (mButtonExit != null) {
            mButtonExit.setOnClickListener(this);
        }
        if (mButtonBackCamera != null) {
            mButtonBackCamera.setOnClickListener(this);
        }
        if (mButtonFrontCamera != null) {
            mButtonFrontCamera.setOnClickListener(this);
        }
        view.findViewById(R.id.info).setOnClickListener(this);
        mCameraView = (CameraView) view.findViewById(R.id.texture);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
            mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
            if (mVideoPreview != null) {
                mVideoPreview.prepare();
            }
        }
        if (mCurrentApiVersion  >= Build.VERSION_CODES.LOLLIPOP) {
            if(mCameraView != null) {
                mCameraView.startBackgroundThread();
                if (mCameraView.isAvailable()) {
                    mCameraView.openCamera(mCameraView.getWidth(), mCameraView.getHeight());
                } else {
                   // mCameraView.setSurfaceTextureListener(mSurfaceTextureListener);
                }
                mCameraView.prepare();
            }
        }
    }

    @Override
    public void onPause() {
        if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
            if (mVideoPreview != null) {
                mVideoPreview.halt();
            }
        }
        if (mCurrentApiVersion  >= Build.VERSION_CODES.LOLLIPOP) {
            mCameraView.halt();
            mCameraView.stopBackgroundThread();
        }
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.backcamera: {
                if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
                    createNewVideoPreview(Camera.CameraInfo.CAMERA_FACING_BACK);
                }
                if (mCurrentApiVersion  >= Build.VERSION_CODES.LOLLIPOP) {
                    createNewVideoPreviewEnchanced(0);
                }
            }
            break;
            case R.id.frontcamera: {
                if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
                    createNewVideoPreview(Camera.CameraInfo.CAMERA_FACING_FRONT);
                }
                if (mCurrentApiVersion  >= Build.VERSION_CODES.LOLLIPOP) {
                    createNewVideoPreviewEnchanced(1);
                }
            }
            break;
            case R.id.recordvideo: {
                mRecording = !mRecording;
                Button mButtonRecordVideo = (Button) view.findViewById(R.id.recordvideo);
                if (mRecording) {
                    mButtonRecordVideo.setText("Stop");
                } else {
                    mButtonRecordVideo.setText("Record");
                }
                if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
                   mVideoPreview.setRecordingState(mRecording);
                }
            }
            break;
            case R.id.exit: {
                if (mCurrentApiVersion  >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    mCurrentApiVersion  <= Build.VERSION_CODES.KITKAT) {
                    destroyVideoPreviewWindow();
                }
                Activity activity = getActivity();
                if (activity != null) {
                    activity.finish();
                }
            }
            break;
            case R.id.info: {
                Activity activity = getActivity();
                if (activity != null) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    private void createNewVideoPreview(int cameraId) {
        if (mVideoPreviewFrame != null && mVideoPreview != null) {
            destroyVideoPreviewWindow();
        }
        if (mVideoPreviewFrame != null && mVideoPreview == null) {
            createVideoPreviewWindow(cameraId);
        }
    }

    private void createNewVideoPreviewEnchanced(int cameraId) {
        if (mVideoPreviewFrame != null && mCameraView != null) {
            destroyVideoPreviewWindowEnhanced();
        }
        if (mVideoPreviewFrame != null && mCameraView == null) {
            createVideoPreviewWindowEnhanced();
        }
    }

    private void destroyVideoPreviewWindow() {
        if (mVideoPreview != null) {
            mVideoPreview.halt();
            mVideoPreviewFrame.removeAllViews();
            mVideoPreview = null;
        }
    }

    private void destroyVideoPreviewWindowEnhanced() {
        if (mCameraView != null) {
            mCameraView.halt();
            mVideoPreviewFrame.removeAllViews();
            mCameraView = null;
        }
    }

    private void createVideoPreviewWindowEnhanced( ) {
        if (mCameraView != null) {
            mCameraView.mRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            mCameraView.prepare();
        }
    }

    private void createVideoPreviewWindow(int activeCam) {
        Log.d(TAG, "Creating initial VideoPreview!");
         previewWindowSetup(activeCam);
         mVideoPreviewFrame.addView(mVideoPreview);
   }

    private void previewWindowSetup(int activeCam) {
        int mEncodingWidth = 1280;
        int mEncodingHeight = 720;
        // add surface listeners
        mVideoPreview = new VideoPreview(getActivity());
        mVideoPreview.prepare();
        mVideoPreview.setActiveCamera(activeCam);
        // set camera rotation
        mVideoPreview.mRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        // set layout
        if (mVideoPreview.getPreviewSizeHeight() == mEncodingHeight &&
                 mVideoPreview.getPreviewSizeWidth() == mEncodingWidth) {
            mVideoPreview.setLayoutParams(new FrameLayout.LayoutParams(
                    mVideoPreview.getPreviewSizeWidth(),
                    mVideoPreview.getPreviewSizeHeight(),
                    Gravity.CENTER));
        } else {
            mVideoPreview.setLayoutParams(new FrameLayout.LayoutParams(
                    mEncodingWidth,
                    mEncodingHeight,
                    Gravity.CENTER));
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mVideoPreview.setAspectRatio(16, 9);
        }
    }
}