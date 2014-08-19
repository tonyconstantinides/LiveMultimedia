package com.constantinnovationsinc.livemultimedia.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;

import java.io.File;

import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;

import com.constantinnovationsinc.livemultimedia.R;

public class Camera2VideoFragment extends Fragment implements View.OnClickListener {
    private static final String TAG =  Camera2VideoFragment.class.getName();
    private FrameLayout   mVideoPreviewFrame;
    private VideoPreview  mVideoPreview;
    private Button mButtonBackCamera;
    private Button mButtonFrontCamera;
    private Button mButtonRecordVideo;
    private Boolean mRecording = false;

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

        if (view != null &&  mVideoPreviewFrame != null) {
           createVideoPreviewWindow();
        }
        return view;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mButtonRecordVideo.setOnClickListener(this);
        mButtonBackCamera.setOnClickListener(this);
        mButtonFrontCamera.setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
     }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.backcamera: {
                    VideoPreview.mActiveCamera = VideoPreview.BACK_CAMERA;
                    createNewVideoPreview();
                }
                break;
            case R.id.frontcamera: {
                    VideoPreview.mActiveCamera = VideoPreview.FRONT_CAMERA;
                    createNewVideoPreview();
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
            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    private File getVideoFile(Context context) {
        return new File(context.getExternalFilesDir(null), "video.mp4");
    }

    private void  createNewVideoPreview() {
        if ( mVideoPreviewFrame  != null && mVideoPreview != null ) {
            destroyVideoPreviewWindow();
        }
        if ( mVideoPreviewFrame  != null && mVideoPreview == null ) {
            createVideoPreviewWindow();
        }
    }

    public void destroyVideoPreviewWindow() {
        mVideoPreview.halt();
        mVideoPreviewFrame.removeAllViews();
        mVideoPreview = null;
    }

    public void createVideoPreviewWindow() {
        Log.d(TAG, "Creating initial VideoPreview!");
        mVideoPreview = new VideoPreview(getActivity().getApplicationContext());
        mVideoPreview.prepare();
        mVideoPreview.setLayoutParams(new FrameLayout.LayoutParams(
                mVideoPreview.getPreviewSizeWidth(),
                mVideoPreview.getPreviewSizeHeight(),
                Gravity.CENTER));
        mVideoPreviewFrame.addView(mVideoPreview);
    }
}