package com.constantinnovationsinc.livemultimedia.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.hardware.Camera;
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
import com.constantinnovationsinc.livemultimedia.R;

public class Camera2VideoFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = Camera2VideoFragment.class.getName();
    private FrameLayout mVideoPreviewFrame;
    private VideoPreview mVideoPreview;
    private Button mButtonBackCamera;
    private Button mButtonFrontCamera;
    private Button mButtonRecordVideo;
    private Button mButtonExit;
    private Boolean mRecording = false;
    private int mEncodingWidth = 1280;
    private int mEncodingHeight = 720;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView for Camera2VideoFragment");
        View view = inflater.inflate(R.layout.fragment_camera2_video, container, false);
        mVideoPreviewFrame = (FrameLayout) view.findViewById(R.id.videoPreviewFrame);
        mButtonBackCamera = (Button) view.findViewById(R.id.backcamera);
        mButtonFrontCamera = (Button) view.findViewById(R.id.frontcamera);
        mButtonRecordVideo = (Button) view.findViewById(R.id.recordvideo);
        mButtonExit        = (Button) view.findViewById(R.id.exit);
        if (view != null && mVideoPreviewFrame != null) {
            createVideoPreviewWindow(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
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
         return view;
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mVideoPreview != null) {
            mVideoPreview.prepare();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoPreview != null) {
            mVideoPreview.halt();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.backcamera: {
                createNewVideoPreview(Camera.CameraInfo.CAMERA_FACING_BACK);
            }
            break;
            case R.id.frontcamera: {
                createNewVideoPreview(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
            break;
            case R.id.recordvideo: {
                mRecording = !mRecording;
                if (mRecording) {
                    mButtonRecordVideo.setText("Stop");
                } else {
                    mButtonRecordVideo.setText("Record");
                }
                mVideoPreview.setRecordingState(mRecording);
            }
            break;
            case R.id.exit: {
                destroyVideoPreviewWindow();
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

    public void destroyVideoPreviewWindow() {
        if (mVideoPreview != null) {
            mVideoPreview.halt();
            mVideoPreviewFrame.removeAllViews();
            mVideoPreview = null;
        }
    }

    public void createVideoPreviewWindow(int activeCam) {
        Log.d(TAG, "Creating initial VideoPreview!");
        previewWindowSetup(activeCam);
        mVideoPreviewFrame.addView(mVideoPreview);
    }

    public void previewWindowSetup(int activeCam) {
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