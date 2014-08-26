package com.constantinnovationsinc.livemultimedia.previews;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.os.Process;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import java.lang.RuntimeException;

import com.constantinnovationsinc.livemultimedia.callbacks.FramesReadyCallback;
import com.constantinnovationsinc.livemultimedia.cameras.JellyBeanCamera;
import com.constantinnovationsinc.livemultimedia.encoders.AudioEncoder;
import com.constantinnovationsinc.livemultimedia.encoders.GPUEncoder;
import com.constantinnovationsinc.livemultimedia.recorders.AVRecorder;

/****************************************************************************************************************
 * This class provides the camera app preview that Android requires if you going to operate the hardware camera
 * This preview serves two purposes. Firstly it allows the user to see what the camera sees and it also allows
 * the app to capture video frame from the preview window.
 ***************************************************************************************************************/
public class VideoPreview extends TextureView implements SurfaceTextureListener, FramesReadyCallback {
    private static final String TAG = VideoPreview.class.getSimpleName();
    private static final String START_CAPTURE_FRAMES_SOUND = "StartCaptureSound";

    private JellyBeanCamera mCamera = null;
    private AVRecorder mAVRecorder = null;
    private Context mContext = null;
    private Handler mCameraHandler = null;
    private Handler mVideoEncoderHandler = null;
    private Handler mAudioEncoderHandler = null;
    private HandlerThread mCameraThread = null;
    private HandlerThread mVideoEncoderThread = null;
    private HandlerThread mAudioEncodingThread = null;
    private AudioEncoder mAudioEncoder = null;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    public  int mActiveCameraId = -1;
    public  int mRotation = -1;
    public Boolean recordStarted = false;
    public  FramesReadyCallback  mVideoFramesReadylistener = null;

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
        setFrameReadyListener(this);
   }

    public void halt() {
        Log.d(TAG, "Camera released and SurfaceTextureListener is not null!");
        setSurfaceTextureListener(null);
        if (mCamera != null) {
            mCamera.stopCamera();
            mCamera = null;
        }
    }

    public void release() {
       if (mCameraThread != null) {
            mCameraThread.quitSafely();
       }
        if (mVideoEncoderThread != null) {
            mVideoEncoderThread.quitSafely();
        }
        if ( mAudioEncodingThread != null) {
            mAudioEncodingThread.quitSafely();
        }
        mCameraThread = null;
        mVideoEncoderHandler = null;
        mAudioEncoderHandler = null;
    }

    /*************************************************************************************************
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     ************************************************************************************************/
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
            if (width < (height * (mRatioWidth / mRatioHeight))) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
               setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    /*********************************************************************************************
    * As the surface is being created the camera preview size can be set
    * This may be called multiple times during the app as the user starts and stops the camera
    * Each time a new surface may be created and a new preview window set
    ***********************************************************************************************/
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture image,
                                          int arg1,
                                          int arg2) {
        Log.d(TAG, "SurfaceTexture now Available!");
        final SurfaceTexture texture = image;
        if (mCameraThread == null) {
            mCameraThread = new HandlerThread("camThread");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());
        }
        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "CameraThread Running!");
                if (mCamera == null) {
                    Log.e(TAG, "Camera object is null, leaving...");
                    return;
                 }
                int camCount = mCamera.getNumberOfCameras();
                if (camCount == 0) {
                    Log.e(TAG, "No Cameras found, exiting!");
                    throw new RuntimeException("Unable to open camera");
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
                mCamera.setPreviewTexture(texture);
                Log.d(TAG, "Starting the camera preview..");
                mCamera.startVideoPreview();
            }
        });

        setAlpha(1.0f);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRotation(90.0f);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        Log.d(TAG, "SurfaceTexture now Destroyed!");
        release();
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

    public void setActiveCamera(int activeCam) {
        mActiveCameraId = activeCam;
    }

    public void setFrameReadyListener(FramesReadyCallback listener) {
        mVideoFramesReadylistener  = listener;
    }

    public synchronized void setRecordingState(Boolean state) {
        if (mCamera != null) {
            mCamera.setRecordingState(state);
        }
        if (state && mContext != null && mCamera != null) {
            createAVRecorder();
            recordAudio();
        }
    }

    public synchronized void createAVRecorder() {
        if (mCamera == null) {
            Log.e(TAG, "Null Camera in createAVRecorder() method!");
            return;
        }
        if (mCamera.mFrameCatcher == null) {
            Log.e(TAG, "Null FrameCatcher  in createAVRecorder() method!");
            return;
        }
        if (mCamera.mFrameCatcher.mRecording) {
            mAVRecorder = new AVRecorder(mContext);
            mAVRecorder.setSize((int)mCamera.getPreviewSizeWidth(), (int)mCamera.getPreviewSizeHeight());
            mAVRecorder.setEncodingWidth((int)mCamera.getPreviewSizeWidth());
            mAVRecorder.setEncodingHeight((int) mCamera.getPreviewSizeHeight());
            mAVRecorder.prepare();
            mCamera.setOnFramesReadyCallBack(this);
        }
      }

    public synchronized void recordAudio() {
        // record audio on the same threade you encode
       startAudioEncoder();
    }

    public synchronized void startAudioEncoder() {
        try {
            if (mAVRecorder != null) {
                mAudioEncoder = mAVRecorder.getAudioEncoder();
                if (mAudioEncoder != null) {
                    if (mAudioEncoderHandler == null) {
                        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
                            public void uncaughtException(Thread th, Throwable ex) {
                                Log.e(TAG, "Uncaught exception: in Audio Encoder " + ex);
                            }
                        };
                        mAudioEncodingThread = new HandlerThread("AudioEncoderThread", Process.THREAD_PRIORITY_AUDIO);
                        mAudioEncodingThread.setUncaughtExceptionHandler(handler);
                        mAudioEncodingThread.start();
                        mAudioEncoderHandler = new Handler(mAudioEncodingThread.getLooper());
                        mAudioEncoderHandler.post(mAudioEncoder);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public synchronized void stopAudioRecording() {
        try {
            if (mAudioEncodingThread == null) {
                Log.e(TAG, "Audio Encoding thread is dead!!");
                return;
            }
            if (mAVRecorder != null) {
                if (mAudioEncodingThread != null) {
                    if (mAudioEncodingThread.isAlive()) {
                        if (mAudioEncoder != null) {
                            // just kill the thread;
                            //mAudioEncodingThread.quitSafely();
                        } else {
                            Log.e(TAG, "Audio Encoder is null in stopAudioRecording!");
                        }
                    }
                } else {
                    Log.e(TAG, "Audio Encoding thread is not alive in stopAudioRecording()");
                }
            } else {
                Log.e(TAG, "AVRecorder is null in stopAudioRecording()");
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public synchronized void startVideoEncoder() {
        stopAudioRecording();
        // passed the shared memory reference from the catcher to the recorder
        if (mAVRecorder.setSharedMemFile(mCamera.mFrameCatcher.getSharedMemFile())) {
            GPUEncoder encoder = mAVRecorder.getVideoEncoder();
            if (encoder != null) {
                if (mVideoEncoderHandler == null) {
                    try {
                        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
                            public void uncaughtException(Thread th, Throwable ex) {
                                Log.e(TAG, "Uncaught exception: in Video Encoder " + ex);
                            }
                        };
                        mVideoEncoderThread = new HandlerThread("GPUEncoderThread");
                        mVideoEncoderThread.setUncaughtExceptionHandler(handler);
                        if (mAVRecorder.getVideoEncoder() != null && mCamera != null) {
                            mAVRecorder.getVideoEncoder().setSharedVideoFramesStore(mCamera.mFrameCatcher.getSharedMemFile());
                        }
                        mVideoEncoderThread.start();
                        mVideoEncoderHandler = new Handler(mVideoEncoderThread.getLooper());
                        mVideoEncoderHandler.post(mVideoEncoderThread);
                        mAVRecorder.getVideoEncoder().runGPUEncoder();
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        } else {
            Log.e(TAG, "Unable to start AVRecorder because  SharedMemoryFile from catcher is null in AVRecorder!");
        }
    }

   public synchronized void playLazerSound() {
      if (mAVRecorder != null) {
          mAVRecorder.playSound(START_CAPTURE_FRAMES_SOUND);
      } else {
           Log.e(TAG, "mACRecorder is null in the VideoPreview");
      }
   }

}