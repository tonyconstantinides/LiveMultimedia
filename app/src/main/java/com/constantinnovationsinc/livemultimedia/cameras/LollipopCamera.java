package com.constantinnovationsinc.livemultimedia.cameras;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.constantinnovationsinc.livemultimedia.views.AutoFitTextureView;
import com.constantinnovationsinc.livemultimedia.threads.ImageSaverThread;
import com.constantinnovationsinc.livemultimedia.utilities.CompareSizesByArea;
import com.constantinnovationsinc.livemultimedia.views.CameraView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class LollipopCamera  extends BaseCamera {
   private static final String TAG = LollipopCamera.class.getCanonicalName();
   private static final int STATE_PREVIEW = 0;
   private static final int STATE_WAITING_LOCK = 1;
   private static final int STATE_WAITING_PRECAPTURE = 2;
   private static final int STATE_WAITING_NON_PRECAPTURE = 3;
   private static final int STATE_PICTURE_TAKEN = 4;
   private FragmentActivity mActivity = null;
   private CameraManager mCameraManager = null;
   private CameraMetadata mMetaData = null;
   private CameraCaptureSession mCaptureSession = null;
   private CameraCaptureSession mPreviewSession = null;
   private CameraCaptureSession.CaptureCallback mCaptureCallback = null;
   private CameraDevice  mCameraDevice = null;
   private CameraDevice.StateCallback mStateCallback = null;
   private CaptureRequest.Builder mPreviewRequestBuilder = null;
   private CaptureRequest mPreviewRequest = null;
   private Size mPreviewSize = null;
   private HandlerThread mBackgroundThread = null;
   private Handler mBackgroundHandler = null;
   private ImageReader mImageReader = null;
   private File mFile = null;
   private ImageReader.OnImageAvailableListener mOnImageAvailableListener = null;
   private int mState = STATE_PREVIEW;
   private Handler mMessageHandler = null;
   private Semaphore mCameraOpenCloseLock = new Semaphore(1);
   private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
   // for video
   private boolean mIsRecordingVideo = false;
   private CaptureRequest.Builder mPreviewBuilder = null;
   private MediaRecorder mMediaRecorder = null;
   private Size    mVideoSize = null;
   private CameraView mTextureView = null;

   static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

   public void onFrameAvailable( @NonNull SurfaceTexture surfaceTexture) {
            Log.w(TAG, "Receiving frames from Texture!");
   }

   public  LollipopCamera() {
   }

   public LollipopCamera( @NonNull Context context,  @NonNull CameraView view) {
       super(context);
       Log.d(TAG, "LollipopCamera constructor");
       mActivity = (FragmentActivity)context;
       mTextureView = view;
       mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
   }

   public void setupPictureCapture() {
       Log.d(TAG, "setupPictureCapture");
       setupImageListener();
       setupJPEGCaptureListener();
       setupMessageHandler();
   }

   public void setupVideoCapture() {
       Log.d(TAG, "setupVideoCapture");
       setupCameraStatusListener();
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   public synchronized String getCapabilities(int cameraId) {
       String support = null;
       int    level;
           try {
               if (cameraId == BACK_CAMERA) {
                   String backCameraId = mCameraManager.getCameraIdList()[0];
                   CameraCharacteristics backCameraInfo = mCameraManager.getCameraCharacteristics(backCameraId);
                   level = backCameraInfo.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                   support = determineCameraApiSupport(level);
               } else if (cameraId == FRONT_CAMERA) {
                   String frontCameraId = mCameraManager.getCameraIdList()[1];
                   CameraCharacteristics frontCameraInfo  = mCameraManager.getCameraCharacteristics(frontCameraId);
                   level = frontCameraInfo.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                   support = determineCameraApiSupport(level);
               }
           } catch (CameraAccessException e) {
               e.printStackTrace();
           }
       return support;
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   public synchronized LollipopCamera startFrontCamera() {
       CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
       if (manager != null) {
           try {
               String cameraId = manager.getCameraIdList()[0];
               setupVideoCapture();
           } catch (CameraAccessException e) {
               e.printStackTrace();
           }
       }
       return this;
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   public void setupCameraStatusListener() {
       mStateCallback = new CameraDevice.StateCallback() {
           @Override
           public void onOpened(CameraDevice cameraDevice) {
               Log.d(TAG, "******************************");
               Log.d(TAG, "onOpened camera status!");
               Log.d(TAG, "******************************");
               mCameraDevice = cameraDevice;
               startPreview();
               mCameraOpenCloseLock.release();
               if (null != mTextureView) {
                   configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
               }
           }

           @Override
           public void onClosed(CameraDevice camera) {
               Log.d(TAG, "******************************");
               Log.d(TAG, "onClosed camera status");
               Log.d(TAG, "******************************");
           }

           @Override
           public void onDisconnected(CameraDevice cameraDevice) {
               Log.d(TAG, "******************************");
               Log.d(TAG, "onDisconnected camera status");
               Log.d(TAG, "******************************");
               mCameraOpenCloseLock.release();
               cameraDevice.close();
               mCameraDevice = null;
           }

           @Override
           public void onError(CameraDevice cameraDevice, int error) {
               Log.d(TAG, "******************************");
               Log.d(TAG, "onError camera status");
               Log.d(TAG, "******************************");
               mCameraOpenCloseLock.release();
               cameraDevice.close();
               mCameraDevice = null;
               if (null != mActivity) {
                   mActivity.finish();
               }
           }
       };
   }



   public void  setupImageListener() {
       mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
           @Override
           public void onImageAvailable(ImageReader reader) {
               mBackgroundHandler.post(new ImageSaverThread(reader.acquireNextImage(), mFile));
           }
       };
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   public void  setupJPEGCaptureListener() {
       mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
          private void process(CaptureResult result) {
                   switch (mState) {
                       case STATE_PREVIEW: {
                           // We have nothing to do when the camera preview is working normally.
                           break;
                       }
                       case STATE_WAITING_LOCK: {
                               int afState = result.get(CaptureResult.CONTROL_AF_STATE);
                               if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                                       CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                                   // CONTROL_AE_STATE can be null on some devices
                                   Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                   if (aeState == null ||
                                           aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                       mState = STATE_WAITING_NON_PRECAPTURE;
                                       captureStillPicture();
                                   } else {
                                       runPrecaptureSequence();
                                   }
                               }
                           break;
                       }
                       case STATE_WAITING_PRECAPTURE: {
                               // CONTROL_AE_STATE can be null on some devices
                               Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                               if (aeState == null ||
                                       aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                       aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                                   mState = STATE_WAITING_NON_PRECAPTURE;
                               }
                           break;
                       }
                       case STATE_WAITING_NON_PRECAPTURE: {
                               // CONTROL_AE_STATE can be null on some devices
                               Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                               if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                   mState = STATE_PICTURE_TAKEN;
                                   captureStillPicture();
                               }
                           break;
                       }
                   }
               }
           };
    }

    /**************************************************
     * A {@link Handler} for showing {@link Toast}s.
     *************************************************/
    private void setupMessageHandler() {
        mMessageHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (mActivity != null) {
                    Toast.makeText(mActivity, (String) msg.obj, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }


      /************************************************************************************************
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes larger
     * than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     ***********************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /***************************************************************************************
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     **************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void openCamera(int width, int height) {
        if (null == mActivity || mActivity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == mActivity.getResources().getConfiguration().ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(mActivity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            mActivity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            new ErrorDialog().show(mActivity.getFragmentManager(), "dialog");
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    /*********************************************************
     * Close the camera.
     ********************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }


    /******************************************************************
    * Creates a new {@link CameraCaptureSession} for camera preview.
    ******************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /******************************************
     * Start the camera preview. Use for video
     ****************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startPreview() {
        Log.d(TAG, "******************************");
        Log.d(TAG, "startPreview()");
        Log.d(TAG, "******************************");

        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "******************************");
                    Log.d(TAG, "onConfigured()");
                    Log.d(TAG, "******************************");
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "******************************");
                    Log.d(TAG, "onConfiguredFailed()");
                    Log.d(TAG, "******************************");
                    if (null != mActivity) {
                        Toast.makeText(mActivity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**********************************************************************************************
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     *********************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize || null == mActivity) {
            return;
        }
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /********************************
    * Initiate a still image capture.
    *********************************/
    private void takePicture() {
           lockFocus();
    }

    /****************************************************************
    * Lock the focus as the first step for a still image capture.
    ***************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void lockFocus() {
        try {
                // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /************************************************************************************************
    * Unlock the focus. This method should be called when still image capture sequence is finished.
    ************************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlockFocus() {
        try {
            // Reset the autofucos trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*************************************************************************************************
    * Run the precapture sequence for capturing a still image. This method should be called when we
    * get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
    *************************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void runPrecaptureSequence() {
           try {
               // This is how to tell the camera to trigger.
               mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                       CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
               // Tell #mCaptureCallback to wait for the precapture sequence to be set.
               mState = STATE_WAITING_PRECAPTURE;
               mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                       mBackgroundHandler);
           } catch (CameraAccessException e) {
               e.printStackTrace();
           }
    }

    /************************************************************************************
    * Capture a still picture. This method should be called when we get a response in
    * {@link #mCaptureCallback} from both {@link #lockFocus()}.
    ************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void captureStillPicture() {
        try {
            if (null == mActivity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    showToast("Saved: " + mFile);
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /******************************************
     * Shows a {@link Toast} on the UI thread.
     * @param text The message to show
     *****************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    /***********************************************************************************************
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     **********************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /*************************************************************************************
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     *************************************************************************************/
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private File getVideoFile(Context context) {
        return new File(context.getExternalFilesDir(null), "video.mp4");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpMediaRecorder() throws IOException {
        if (null == mActivity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFile(mActivity).getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    private String determineCameraApiSupport(int level) {
        String support;
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            support = LEGACY;
        }  else if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
            support = LIMITED;
        }  else if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) {
            support = FULL;
        } else {
            support = LEGACY;
        }
        return support;
    }

    private void startRecordingVideo() {
        try {
            // UI
            mIsRecordingVideo = true;
           // Start recording
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        if (null != mActivity) {
            Toast.makeText(mActivity, "Video saved: " + getVideoFile(mActivity),
                    Toast.LENGTH_SHORT).show();
        }
        startPreview();
    }

    public static class ErrorDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage("This device doesn't support Camera2 API.")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }
}
