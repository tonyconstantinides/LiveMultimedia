package com.constantinnovationsinc.livemultimedia.previews;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import com.constantinnovationsinc.livemultimedia.cameras.JellyBeanCamera;
import com.constantinnovationsinc.livemultimedia.recorders.AVRecorder;

/**
 * This class provides the camera app preview that Android requires if you going to operate the hardware camera
 * This preview serves two purposes. Firstly it allows the user to see what the camera sees and it also allows
 * the app to capture video frame from the preview window.
 */
public class VideoPreview extends TextureView implements SurfaceTextureListener  {
    private static final String TAG = VideoPreview.class.getSimpleName();
    private JellyBeanCamera mCamera = null;
    private AVRecorder mAVRecorder = null;
    private Context mContext = null;
    private Thread mCameraThread = null;
    private Thread mEncoderThread = null;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    public static final String BACK_CAMERA = "BACK_CAMERA";
    public static final String FRONT_CAMERA = "FRONT_CAMERA";
    public Boolean recordStarted = false;
    public static String  mActiveCamera = BACK_CAMERA;

    public VideoPreview(Context context) {
        super(context);
        mContext = context;
    }

    public VideoPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public VideoPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    public void prepare() {
        Log.d(TAG, "Camera created as well as SurfaceTextureListener!");
        mCamera = new JellyBeanCamera(mContext, this);
        setSurfaceTextureListener(this);
   }

    public void halt() {
        Log.d(TAG, "Camera released and SurfaceTextureListener is not null!");
        setSurfaceTextureListener(null);
        if (mCamera != null) {
            mCamera.stopCamera();
            mCamera = null;
        }
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    /** As the surface is being created the camera preview size can be set
     * This may be called multiple times during the app as the user starts and stops the camera
     * Each time a new surface may be created and a new preview window set
    */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture arg0, int arg1,
                                          int arg2) {
        Log.d(TAG, "SurfaceTexture now Available!");
        final SurfaceTexture texture = arg0;
        if (mCameraThread == null) {
            mCameraThread = new Thread("camThread") {
                @Override
                public void run() {
                    Log.d(TAG, "CameraThread Running!");
                    if (mActiveCamera == BACK_CAMERA) {
                        mCamera.startBackCamera();
                    } else if (mActiveCamera == FRONT_CAMERA) {
                        mCamera.startFrontCamera();
                    }
                    mCamera.setupPreviewWindow();
                    mCamera.setupVideoCaptureMethod();
                    mCamera.setPreviewTexture(texture);
                    Log.d(TAG, "Starting the camera preview..");
                    mCamera.startVideoPreview();
                }
            };
            mCameraThread.start();
        }
        setAlpha(1.0f);
        setRotation(90.0f);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        Log.d(TAG, "SurfaceTexture now Destroyed!");
        mCamera.stopCamera();
        mCameraThread = null;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int arg1,
                                            int arg2) {
        Log.d(TAG, "SurfaceTexture size has changed!");
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
    }

    public int getPreviewSizeWidth() {
       return (int)mCamera.getPreviewSizeWidth();
    }
    public int getPreviewSizeHeight() {
       return (int)mCamera.getPreviewSizeHeight();
    }

    public void setRecordingState(Boolean state) {
        if (mCamera != null) {
            mCamera.setRecordingState(state);
        }
        if (state && mContext != null  && mEncoderThread  == null && mCamera != null) {
            mAVRecorder = new AVRecorder(mContext,  mCamera.getSharedMemFile());
                if ( mAVRecorder == null) {
                    mEncoderThread = new Thread("EncodingThread") {
                    @Override
                    public void run() {
                        Log.d(TAG, "Encoding thread running!");
                        mAVRecorder.prepare();
                        mAVRecorder.runEncoderLoop();
                    }
                };
                mEncoderThread.start();
            }
        }
    }
}

