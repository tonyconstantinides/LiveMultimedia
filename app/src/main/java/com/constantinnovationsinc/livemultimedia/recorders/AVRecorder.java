package com.constantinnovationsinc.livemultimedia.recorders;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;
import android.app.Activity;
import android.os.Environment;
import android.media.SoundPool;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder.AudioSource;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;

import com.constantinnovationsinc.livemultimedia.app.MultimediaApp;
import com.constantinnovationsinc.livemultimedia.encoders.GPUEncoder;
import com.constantinnovationsinc.livemultimedia.encoders.AudioEncoder;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;
import com.constantinnovationsinc.livemultimedia.R;

/**`
 * Created by constantinnovationsinc on 8/17/14.
 */
public class AVRecorder implements Runnable{
    private static final String TAG = AVRecorder.class.getName();
    private static final String ENCODING_THREAD_NAME = "AVEncodingThread";
    private static final String START_CAPTURE_FRAMES_SOUND = "StartCaptureSound";
    private static final String START_ENCODERS_SOUND = "StartEncodersSound";
    private static final String MP4_FILE_WRITTEN_SOUND = "MP4FileWrittenSound";

    /**
     * How long to wait for the next buffer to become available.
     */
    private static final int TIMEOUT_USEC = 10000;
    /**
     * Where to output the test files.
     */
    private static final int FRAME_RATE = 30;
    private static final File OUTPUT_FILENAME_DIR = Environment.getExternalStorageDirectory();
    // parameters for the video encoder
    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding

    private static Boolean mAudioRecordingStopped = false;
    /** Context */
    private Context mContext = null;
    /** Encoders */
    private GPUEncoder mVideoEncoder = null;
    private AudioEncoder mAudioEncoder = null;
    /** Encoder status */
    private Boolean mEncodingStarted = false;

    /** SharedVideoMemory that holds all vidoe buffers */
    private SharedVideoMemory mSharedMemFile = null;

    private Boolean mSoundLoaded = false;
    private SoundPool soundPool = null;
    private HashMap<Integer, Integer> soundPoolMap;
    private int soundID = 1;
    private int mEncodingWidth = 0;
    private int mEncodingHeight = 0;
    private int mProcessAudioFrames = 0;
    /**
     * Width of the output frames.
     */
    private int mWidth = -1;
    /**
     * Height of the output frames.
     */
    private int mHeight = -1;
    /**
     * The destination file for the encoded output.
     */


    private AVRecorder() {

    }

    @Override
    public void run() {
        prepare();
    }

     /*********************************************************************
     * Constructor
     * @param context - the context associated with this encoding thread
     *********************************************************************/
    public AVRecorder(Context context) {
        mContext = context;
        mEncodingStarted = false;
    }

    public synchronized Boolean setSharedMemFile(SharedVideoMemory mem){
        if (mem == null) {
            Log.e(TAG,"SharedMemoryFile is Null passed to AVRecorder!");
            return false;
        }
        mSharedMemFile = mem;
        return true;
    }

    public synchronized void prepare() {
        if (!mEncodingStarted) {
            Log.w(TAG, "AVRecorder prepare executing....");
            try {
                mVideoEncoder = new GPUEncoder((MultimediaApp)mContext.getApplicationContext(), this, mWidth, mHeight);
                mAudioEncoder = new AudioEncoder((MultimediaApp) mContext.getApplicationContext());
                mVideoEncoder.prepare();
                mAudioEncoder.prepare();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }


    public synchronized void playSound(String sound) {
        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                mSoundLoaded = true;
                Activity activity = (Activity)mContext;
                if (activity != null) {
                    AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
                    float curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)maxVolume, 0);
                    float leftVolume = 1f;
                    float rightVolume = 1f;
                    int priority = 1;
                    int no_loop = 0;
                    float normal_playback_rate = 1f;
                    int soundSuccess = soundPool.play(soundID, leftVolume, rightVolume, priority, no_loop, normal_playback_rate);
                    if (soundSuccess == 0) {
                        Log.e(TAG, "Sound failed to play");
                    }
                }
            }
        });
        if (sound.equalsIgnoreCase( START_CAPTURE_FRAMES_SOUND )) {
            soundID = soundPool.load(mContext, R.raw.powerup, 1);
        } else if (sound.equalsIgnoreCase( START_ENCODERS_SOUND )) {
            soundID = soundPool.load(mContext, R.raw.swish, 1);
        } else if (sound.equalsIgnoreCase(MP4_FILE_WRITTEN_SOUND)) {
            soundID = soundPool.load(mContext, R.raw.mp4written, 1);
        }
    }


    public synchronized void release() {
        Log.w(TAG, "Performaing cleanup of AVRecorder");
        mVideoEncoder.release();
        mAudioEncoder.release();
    }

    public synchronized ByteBuffer getNextAudioFrame() {
        ByteBuffer audioFrame = null;
        int audioFramesLeft =  mAudioEncoder.getRemainingAudioFramesCount();
        if (audioFramesLeft > 0) {
              audioFrame = mAudioEncoder.getNextAudioFrame();
        }
        return audioFrame;
    }

    public synchronized AudioEncoder getAudioEncoder() {
        return mAudioEncoder;
    }

    public synchronized GPUEncoder getVideoEncoder() {
        return mVideoEncoder;
    }

    public synchronized MediaFormat getAudioFormat() {
        return mAudioEncoder.getAudioFormat();
    }

    /*********************************
     * Sets the desired frame size.
     *********************************/
    public synchronized void setSize(int width, int height) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mWidth = width;
        mHeight = height;
    }

    public synchronized void setEncodingWidth(int width) {
        mEncodingWidth = width;
    }

    public synchronized void setEncodingHeight( int height) {
        mEncodingHeight = height;
    }
}