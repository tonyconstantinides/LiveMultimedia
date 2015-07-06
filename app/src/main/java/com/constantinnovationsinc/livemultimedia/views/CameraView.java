package com.constantinnovationsinc.livemultimedia.views;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import com.constantinnovationsinc.livemultimedia.callbacks.FramesReadyCallback;
import com.constantinnovationsinc.livemultimedia.cameras.AndroidCamera;
import com.constantinnovationsinc.livemultimedia.recorders.AVRecorder;


public class CameraView extends TextureView implements FramesReadyCallback{
    private static final String TAG = CameraView.class.getCanonicalName();
    private static final String START_CAPTURE_FRAMES_SOUND = "StartCaptureSound";
    private static final String START_ENCODERS_SOUND = "StartEncodersSound";
    private AVRecorder mAVRecorder = null;
    private Context    mContext = null;
    private FragmentActivity   mActivity = null;
    private AndroidCamera mCamera = null;
    private int mRatioWidth = -1;
    private int mRatioHeight = -1;
    public int mRotation = -1;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = null;

    public CameraView(Context context) {
        super(context);
        mContext = context;
        mActivity = (FragmentActivity)mContext;
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext = context;
        mActivity = (FragmentActivity)mContext;
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mActivity = (FragmentActivity)mContext;
    }

    public void prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCamera = new AndroidCamera(mContext, AndroidCamera.VIDEO, this);
            setupSurfaceTexureListener();
            setupVideoCapture();
        }
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void openCamera(int width, int height ) {
        mCamera.openCamera(width,height);
    }

    public void halt() {
        Log.d(TAG, "Camera released and SurfaceTextureListener is not null!");
        setSurfaceTextureListener(null);
        if (mCamera != null) {
            mCamera.stopCamera();
            mCamera = null;
        }
    }

    public int getVideoWidth() {
        return 1920;
    }

    public int getVideoHeight() {
        return 1080;
    }



    /**
     * **********************************************************************************************
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     ***********************************************************************************************/
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
        Log.d(TAG, "VideoPreview Window being measured!");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < (height * (mRatioWidth / mRatioHeight))) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    public void setupVideoCapture() {
         mCamera.setupVideoCapture();
    }

    public void setupSurfaceTexureListener() {
        if (mSurfaceTextureListener != null)
            return;
        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                                  int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable() about to open the camera with width,height "
                        + String.valueOf(width) + "," + String.valueOf(height));
                mCamera.openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                    int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged() width width,height "
                        + String.valueOf(width) + "," + String.valueOf(height));
                mCamera.configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "onSurfaceTextureDestroyed() ");
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "onSurfaceTextureUpdated() ");
            }

        };
    }


    public void setVideoMode() {
    }

    public void setPhotoMode() {

    }

    public void setPhotoVideoMode() {

    }

    public void recordAudio() {

    }

    public void startVideoEncoder() {

    }

    public void playLazerSound() {

    }
}
