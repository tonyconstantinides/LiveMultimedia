package com.constantinnovationsinc.livemultimedia.encoders;

import android.app.Application;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodecInfo;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Log;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import android.media.MediaPlayer.TrackInfo;

import com.constantinnovationsinc.livemultimedia.app.MultimediaApp;

import java.util.LinkedList;
import java.util.List;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * Created by constantinnovationsinc on 8/17/14.
 */
public class AudioEncoder  implements Runnable{
    private static final String TAG = AudioEncoder.class.getName();
    private static final File OUTPUT_FILENAME_DIR = Environment.getExternalStorageDirectory();
    // parameters for the audio encoder
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

    // Audio Encoder and Configuration
    //
    private MediaMuxer mAudioMuxer;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private TrackInfo mAudioTrackInfo;
    private MediaFormat mAudioFormat;	// Configured with the options below
    private boolean mMuxerStarted;
    private TrackIndex mAudioTrackIndex = new TrackIndex();
    private final int kAACProfiles[] = {
            2 /* OMX_AUDIO_AACObjectLC */,
            5 /* OMX_AUDIO_AACObjectHE */,
            39 /* OMX_AUDIO_AACObjectELD */
    };
    private final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
    private final int kBitRates[] = { 64000, 128000 };
    private static final int kNumInputBytes = 256 * 1024;
    private static final long kTimeoutUs = 10000;

    private AudioRecord mRecorder = null;
    private Boolean mAudioRecordingStopped = false;
    public int mAudioFrames = 0;
    public int mAudioFramesMax = 270;
    public MultimediaApp mApp = null;

    public AudioEncoder(Application app) {
        mApp = (MultimediaApp)app;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        recordAudio();
    }

    public synchronized int getRemainingAudioFramesCount() {
        return  mApp.capacity();
    }

    public synchronized ByteBuffer getNextAudioFrame() {
        return  mApp.pullAudioData();
    }

    public synchronized  void prepare( ) {
        Log.w(TAG, "******Begin Audio Encoding***********");
    }

    public synchronized  void recordAudio() {
        try {
            short[][]   buffers  = new short[256][160];
            int         ix       = 0;
            int minBufferSize = AudioRecord.getMinBufferSize(8000,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    8000,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 10);
            mRecorder.startRecording();
            while(!mAudioRecordingStopped ) {
                short[] buffer = buffers[ix++ % buffers.length];
                int readStatus = mRecorder.read(buffer, 0, buffer.length);
                if (readStatus == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Error reading audio data");
                    mAudioRecordingStopped = true;
                }
                if (readStatus == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Error Invalid operation");
                    mAudioRecordingStopped = true;
                }
                // must convert short[] tp byte[]
                ByteBuffer audioBuffer = ByteBuffer.allocate(buffer.length * 2);
                audioBuffer.order(ByteOrder.LITTLE_ENDIAN);
                audioBuffer.asShortBuffer().put(buffer);
                if (mApp.capacity() > 0) {
                    mApp.saveAudioData(audioBuffer);
                    mAudioFrames++;
                }
            }
        } catch(IllegalArgumentException ex) {
            Log.w(TAG,"Error reading voice audio " + ex.toString());
        } catch (IllegalStateException ex) {
            Log.w(TAG, "Error StateException audio " + ex.toString());
        }finally {
            mAudioRecordingStopped = true;
        }
    }

    public synchronized void stopAudioRecording() {
    }

    public synchronized  void release() {
        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
        }
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        mAudioEncoder = null;
    }

    /*************************************************************
     * Checks if external storage is available for read and write
     * @return can I write to a sd card
     **************************************************************/
    private synchronized boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    public synchronized void createAudioMuxer() throws IllegalStateException{
        if (Thread.currentThread().isInterrupted()) {
                release();
        }
        if ( isExternalStorageWritable()) {
            File encodedFile = new File(OUTPUT_FILENAME_DIR, "/movies/EncodedAudio.mp4");
            if (encodedFile.exists()) {
                boolean result = encodedFile.delete();
                if (!result)
                     throw new IllegalStateException("Unable to delete video file");
            }
            String outputPath = encodedFile.toString();
            int format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            try {
                mAudioMuxer = new MediaMuxer(outputPath, format);
            } catch (IOException e) {
                Log.e(TAG, "Audio temp Muxer failed to create!!");
            }
        }
    }

    public synchronized void createAudioFormat() {
        if (Thread.currentThread().isInterrupted()) {
            release();
        }
        mAudioFormat  = new MediaFormat();
        mAudioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
    }



    public synchronized MediaFormat getAudioFormat() {
        return mAudioFormat;
    }


    private synchronized List<String> getEncoderNamesForType(String mime) {
        LinkedList<String> names = new LinkedList<String>();
        MediaCodecInfo[] codecInfo = null;
        MediaCodecInfo info = null;
        int codecTotalNum = 0;
        int sdk = android.os.Build.VERSION.SDK_INT;
        if(sdk <= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            codecTotalNum = MediaCodecList.getCodecCount();
        } else {
            MediaCodecList   codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            codecInfo     = codecList.getCodecInfos();
            codecTotalNum = codecInfo.length;
        }

        for (int i = 0; i < codecTotalNum; ++i) {
            if(sdk <= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                info = MediaCodecList.getCodecInfoAt(i);
            } else {
                if (codecInfo != null) {
                    info = codecInfo[i];
                }
            }
            if (info instanceof MediaCodecInfo && !info.isEncoder()) {
                continue;
            }
            if (!info.getName().startsWith("OMX.")) {
                // Unfortunately for legacy reasons, "AACEncoder", a
                // non OMX component had to be in this list for the video
                // editor code to work... but it cannot actually be instantiated
                // using MediaCodec.
                Log.d(TAG, "skipping '" + info.getName() + "'.");
                continue;
            }
            String[] supportedTypes = info.getSupportedTypes();
            for (String type : supportedTypes) {
                if (type.equalsIgnoreCase(mime)) {
                    names.push(info.getName());
                    break;
                }
            }
        }
        return names;
    }

    private synchronized int queueInputBuffer( MediaCodec codec, ByteBuffer[] inputBuffers, int index, byte[] audioBuffer) {
        ByteBuffer buffer = inputBuffers[index];
        buffer.clear();
        int size = buffer.limit();
        byte[] data  = new byte[size];
        /// ******** fill with audio data ************
        if (data.length >= audioBuffer.length) {
            System.arraycopy(audioBuffer, 0, data, 0, audioBuffer.length);
        }
        buffer.put(data);
        // ******************************************
        codec.queueInputBuffer(index, 0 /* offset */, size, 0 /* timeUs */, 0);
        return size;
    }

    private synchronized void dequeueOutputBuffer(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        codec.releaseOutputBuffer(index, false /* render */);
    }

    class TrackIndex {
        int index = 0;
    }

    /*****************************************************************
     * Generates the presentation time for frame N, in microseconds.
     * @param frameIndex the index of teh frame
     * @return long  the new time
     ****************************************************************/
    private synchronized static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / 30;
    }
}
