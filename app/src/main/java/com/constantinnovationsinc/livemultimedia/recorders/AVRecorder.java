package com.constantinnovationsinc.livemultimedia.recorders;

import android.content.Context;
import android.os.Debug;
import android.util.Log;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import com.constantinnovationsinc.livemultimedia.encoders.GPUEncoder;
import com.constantinnovationsinc.livemultimedia.encoders.AudioEncoder;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;

/**
 * Created by constantinnovationsinc on 8/17/14.
 */
public class AVRecorder {
    private static final String TAG = AVRecorder.class.getName();
    private static final String ENCODING_THREAD_NAME = "AVEncodingThread";

    /**
     * How long to wait for the next buffer to become available.
     */
    private static final int TIMEOUT_USEC = 10000;
    /**
     * Where to output the test files.
     */
    private static final File OUTPUT_FILENAME_DIR = Environment.getExternalStorageDirectory();
    // parameters for the video encoder
    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    // parameters for the audio encoder
    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm"; // Advanced Audio Coding
    private static final int OUTPUT_AUDIO_CHANNEL_COUNT = 2; // Must match the input stream.
    private static final int OUTPUT_AUDIO_BIT_RATE = 128 * 1024;
    private static final int OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectHE;
    private static final int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100; // Must match the input stream.
    private static int mVideoTrackIndex = -1;
    private static int mAudioTrackIndex = -1;
    private static Boolean mMuxerStarted = false;

    private int mEncodingWidth = -1;
    private int mEncodingHeight = -1;
    /** Context */
    private Context mContext = null;
    /** Encoders */
    private GPUEncoder mVideoEncoder = null;
    private AudioEncoder mAudioEncoder = null;
    /** Encoder status */
    private Boolean mEncodingStarted = false;
    /** Muxer */
    private MediaMuxer mMuxer = null;
    /** SharedVideoMemory that holds all vidoe buffers */
    private SharedVideoMemory mSharedMemFile = null;
    private long mVideoFrameEncoded = -1;
    private long mVideoTime = -1;

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
    private String mOutputFile;
    private boolean mLastTrackProcessed = false;

    private AVRecorder() {
    }

     /*********************************************************************
     * Constructor
     * @param context - the context associated with this encoding thread
     *********************************************************************/
    public AVRecorder(Context context, SharedVideoMemory sharedMemFile) {
        mContext = context;
        mSharedMemFile = sharedMemFile;
        mVideoEncoder = new GPUEncoder();
        mAudioEncoder = new AudioEncoder();
        mEncodingStarted = false;
    }

    public void prepare() {
        if (!mEncodingStarted) {
            Log.d(TAG, "Encoding thread running!");
            mVideoEncoder.prepare();
            mAudioEncoder.prepare();
            createMediaMuxer();
        }
    }

    public void runEncoderLoop() {
        delayEncoderUntilFrameCount(100);
        // most of the time the encoder sits in this loop
        // encoding frames until there is no more left
        // currently it is encoding faster than I can feed it
        try {
            while (!mSharedMemFile.isEmpty()) {
                Log.d(TAG, "video frames stored and waiting to be encoded: " + mSharedMemFile.getFrameCoount());
                encodeVideoFromBuffer(mVideoEncoder.getCodec(), mVideoEncoder.getColorFormat());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }  finally {
            mVideoEncoder.release();
        }
    }

    /******************************************************************
     * Encode from buffer rather than a surface
     * @param encoder - the mediacodec, currently only H264 is supported
     * @param encoderColorFormat - color format
     ******************************************************************/
    public void encodeVideoFromBuffer(MediaCodec encoder, int encoderColorFormat) {

        if (mLastTrackProcessed)
            return;

        // Loop until the output side is done.
        boolean inputDone = false;
        boolean encoderDone = false;
        boolean outputDone = false;
        final int TIMEOUT_USEC = 10000;
        mVideoFrameEncoded++;
        mVideoTime = mVideoFrameEncoded * 32; // 30 frame a second

        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaFormat decoderOutputFormat = null;
        int checkIndex = 0;
        int badFrames = 0;
        // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
        // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
        // of algebra and assuming that stride==width and sliceHeight==height yields:


        byte[] frameData = new byte[mEncodingWidth * mEncodingHeight * 3 / 2];
        byte[] 	videoFrame = new byte[frameData.length];
        if (!mSharedMemFile.isEmpty() ) {
            mSharedMemFile.getNextFrame(videoFrame, videoFrame.length);
        }
        if (videoFrame != null) {
            System.arraycopy(videoFrame, 0, frameData, 0, videoFrame.length);
        }
        // release it
        videoFrame = null;
        // If we're not done submitting frames, generate a new one and submit it.  By
        // doing this on every loop we're working to ensure that the encoder always has
        // work to do.
        //
        // We don't really want a timeout here, but sometimes there's a delay opening
        // the encoder device, so a short timeout can keep us from spinning hard.
        if (!inputDone) {
            int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            Log.d(TAG, "inputBufIndex=" + inputBufIndex);
            if (inputBufIndex >= 0) {
                if (mSharedMemFile.isEmpty()) {
                    // Send an empty frame with the end-of-stream flag set.  If we set EOS
                    // on a frame with data, that frame data will be ignored, and the
                    // output will be short one frame.
                    encoder.queueInputBuffer(inputBufIndex, 0, 0, mVideoTime,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                    Log.d(TAG, "sent input EOS (with zero-length frame)");
                } else {
                    ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                    // the buffer should be sized to hold one full frame
                    if (inputBuf.capacity() >= frameData.length) {
                        Log.d(TAG,  "buffer resize to hold correct size");
                    } else{
                        Log.e(TAG, "buffer not correct size to fit a frame");
                    }
                    inputBuf.clear();
                    inputBuf.put(frameData);
                    encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, mVideoTime, 0);
                    Log.d(TAG, "submitted frame " + mVideoFrameEncoded + " to hardware encoder");
                }
            } else {
                // either all in use, or we timed out during initial setup
                Log.d(TAG, "input buffer not available");
            }
        }
        // Just out of curiosity.
        long rawSize = 0;
        long encodedSize = 0;


        if (!encoderDone) {
            int encoderStatus = encoder.dequeueOutputBuffer(mVideoEncoder.mBufferInfo , TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Log.d(TAG, "-------------------------------------------------------------");
                Log.d(TAG, "Encoder status: no output from encoder available");
                Log.d(TAG, "-------------------------------------------------------------");

            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = encoder.getOutputBuffers();
                Log.d(TAG, "-------------------------------------------------------------");
                Log.d(TAG, "Encoder status: encoder output buffers changed");
                Log.d(TAG, "-------------------------------------------------------------");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
                Log.d(TAG, "-------------------------------------------------------------");
                Log.d(TAG, "Encoder status: encoder output format changed: " + newFormat);
                Log.d(TAG, "-------------------------------------------------------------");

                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    Log.e(TAG, "Encoder status: Something wrong, format changed twice");
                }
                // now that we have the Magic Goodies, start the muxer
                mVideoTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
                Log.d(TAG, "-------------------------------------------------------------");
                Log.d(TAG, "Muxer started!");
                Log.d(TAG, "-------------------------------------------------------------");
                // reduce the encoded video tracks
                mVideoFrameEncoded = 0;
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else { // encoderStatus >= 0
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                // Codec config info.  Only expected on first packet.  One way to
                // handle this is to manually stuff the data into the MediaFormat
                // and pass that to configure().
                if ((mVideoEncoder.mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mVideoEncoder.mBufferInfo.size = 0;
                }
                if (encodedData == null) {
                    Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                } else  if ( mVideoFrameEncoded  == 1) {
                    Log.d(TAG, "-----------------------------------");
                    Log.d(TAG, "Setting csd-0 on first track");
                    Log.d(TAG, "-----------------------------------");
                    mVideoEncoder.mFormat.setByteBuffer("csd-0", encodedData);
                }
                // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                if (mVideoEncoder.mBufferInfo.size != 0) {
                    encodedData.position( mVideoEncoder.mBufferInfo.offset);
                    encodedData.limit(mVideoEncoder.mBufferInfo.offset + mVideoEncoder.mBufferInfo.size);
                    encodedSize += mVideoEncoder.mBufferInfo.size;
                    Log.d(TAG, "Writing Video data using MediaMuxer");
                    mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoEncoder.mBufferInfo);
                    Log.d(TAG, "sent " + mVideoEncoder.mBufferInfo.size + " bytes to muxer");
                    // pass encoded track to streamer
                    //mStreamer.writeVideoChunk( encodedData.array() );
                }
                // now release the encoder buffer so the MediaCodec can reuse
                encoder.releaseOutputBuffer(encoderStatus, false);
                if ((mVideoEncoder.mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "output EOS");
                    mLastTrackProcessed = true;
                }
            } // END OF ENODER STATUS > 0
        }     // EMD OF ENCODER LOOP
    }

    public void cleanup() {
        Log.d(TAG, "Performaing cleanup of GPUEncoder");
        mVideoEncoder.release();
        mAudioEncoder.release();
        releaseMuxer();
    }

    /**
     * Sets the desired frame size.
     */
    private void setSize(int width, int height) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mWidth = width;
        mHeight = height;
    }


     /********************************************************************************
     * Create a MediaMuxer.  We can't add the video track and start() the muxer here,
     * because our MediaFormat doesn't have the Magic Goodies.  These can only be
     * obtained from the encoder after it has started processing data.
     **********************************************************************************/
    private void createMediaMuxer() {
        Log.d(TAG, "--->createMediaMuxer()");
        File encodedFile = new File(OUTPUT_FILENAME_DIR, "EncodedAV" + "-" + mEncodingWidth + "x" + mEncodingHeight + ".mp4");
        if (encodedFile.exists()) {
            encodedFile.delete();
        }
        String outputPath = encodedFile.toString();
        int format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
        try {
            mMuxer = new MediaMuxer(outputPath, format);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
        mMuxerStarted = false;
    }

    private void releaseMuxer() {
       if(mMuxer!=null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    private void  delayEncoderUntilFrameCount(int frameCount) {
        // delay the encoder because its encodes faster than I can fill it
        // wait until 100 frames are filled
        // Replace this code with Memory mapped file code and a timer
        while (mSharedMemFile != null && mSharedMemFile.getFrameCoount() < frameCount )  {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }

}
