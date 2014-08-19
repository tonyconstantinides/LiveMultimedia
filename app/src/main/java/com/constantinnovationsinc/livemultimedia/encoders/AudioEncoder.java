package com.constantinnovationsinc.livemultimedia.encoders;

import android.media.MediaCodecInfo;
import android.os.Debug;
import android.util.Log;

/**
 * Created by constantinnovationsinc on 8/17/14.
 */
public class AudioEncoder {
    private static final String TAG = AudioEncoder.class.getName();
    // parameters for the audio encoder
    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm"; // Advanced Audio Coding
    private static final int OUTPUT_AUDIO_CHANNEL_COUNT = 2; // Must match the input stream.
    private static final int OUTPUT_AUDIO_BIT_RATE = 128 * 1024;
    private static final int OUTPUT_AUDIO_AAC_PROFILE =
            MediaCodecInfo.CodecProfileLevel.AACObjectHE;
    private static final int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100; // Must match the input stream.

    public synchronized  void prepare( ) {
        Log.d(TAG, "******Begin Audio Encoding***********");

    }

    public synchronized  void release() {

    }


}
