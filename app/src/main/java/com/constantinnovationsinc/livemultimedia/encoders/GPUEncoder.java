/*
*   Copyright 2015 Constant Innovations Inc
*
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/
package com.constantinnovationsinc.livemultimedia.encoders;
import android.app.Application;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.os.Debug;
import android.os.Environment;
import android.os.SystemClock;
import android.text.format.Time;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import com.constantinnovationsinc.livemultimedia.app.MultimediaApp;
import com.constantinnovationsinc.livemultimedia.cameras.JellyBeanCamera;
import com.constantinnovationsinc.livemultimedia.recorders.AVRecorder;
import com.constantinnovationsinc.livemultimedia.utilities.SharedVideoMemory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**********************************************************************************************
 * This calls handles the encoding of the video using the hardware encoder in the GPU
 * The code that contains the preview window and camera will be moved to separate classes soon
 * The audio code will be moved as well, it will be changed over to MediaCodec instead
 * af of using MediarRecorder
 *********************************************************************************************/

public class GPUEncoder implements Runnable{
    private static final String TAG = GPUEncoder.class.getName();
    private static final String START_CAPTURE_FRAMES_SOUND = "StartCaptureSound";
    private static final String START_ENCODERS_SOUND = "StartEncodersSound";
    private static final String MP4_FILE_WRITTEN_SOUND = "MP4FileWrittenSound";
    private static final File OUTPUT_FILENAME_DIR = Environment.getExternalStorageDirectory();
    private static final int ENCODING_WIDTH = 1280;
    private static final int ENCODING_HEIGHT = 720;
    private static final int BITRATE = 6000000;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    private static final boolean WORK_AROUND_BUGS = false;  // avoid fatal codec bugs
    // movie length, in frames
    private static final int NUM_FRAMES = 210;               // 10 seconds of video
    private static int FRAME_RATE = 30;
	private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
	private static final String DEBUG_FILE_NAME_BASE = Environment.getExternalStorageDirectory().getPath() + "/media/";
    private static final String  MIME_TYPE = "video/avc";
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

    private String dirImages = null;
    private byte[] mVvideoFrameData = null;
    private long realTime = -1;
    private int mEncodingWidth  = -1;
    private int mEncodingHeight = -1;
    private long mPreviewWidth  = -1;
    private long mPreviewHeight = -1;
    private int mImageFormat    = -1;
    // used in memory calculation
    private int mFreeMegs = -1;
    private int  mUsedMegs = -1;
    private int mColorFormat = -1;
    // largest color component delta seen (i.e. actual vs. expected)
    private int mLargestColorDelta = -1;
    private int mTrackIndex = -1;
    private long mVideoFrameEncoded = -1;
    private long mVideoTime = -1;
    private boolean mMuxerStarted = false;
    /** Muxer */
    private MediaMuxer mMuxer = null;
    private MediaFormat mNewFormat = null;
    private MediaCodec mCodec = null;
    private SharedVideoMemory mSharedMemFile = null;

    /* -------private complex types --------- */
    private BufferInfo mInfo = null;
    // allocate one of these up front so we don't need to do it every time
    public BufferInfo mBufferInfo = null;
    public MediaFormat mFormat    = null;
    public  JellyBeanCamera mCamera = null;
    private static int mVideoTrackIndex = -1;
    private static int mAudioTrackIndex = -1;
    /** Encoder status */
    private Boolean mEncodingStarted = false;
    private String mOutputFile = null;
    private boolean mLastTrackProcessed = false;
    private AVRecorder mRecorder = null;

    /* Audio */
    private int mAudioFrame = 0;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private MediaPlayer.TrackInfo mAudioTrackInfo;
    private MediaFormat mAudioFormat;
    private static final String mAudioCodecName = "audio/mp4a-latm";
    private static final int kNumInputBytes = 256 * 1024;
    private static final long kTimeoutUs = 10000;
    private MultimediaApp mApp = null;
    private byte[] mCurrentEncodedAudioData = null;
    private Boolean mAudioFeatureActive = false;

    /*********************************************************************
     * Constructor
     *********************************************************************/
    public GPUEncoder(Application app, AVRecorder recorder ,int videoWidth, int videoHeight) {
        mEncodingWidth = videoWidth;
        mEncodingHeight = videoHeight;
        mRecorder = recorder;
        mApp = (MultimediaApp)app;
    }

    public synchronized void setSharedVideoFramesStore(SharedVideoMemory shared) {
        if (shared != null) {
            mSharedMemFile = shared;
        }
    }

    @Override
    public void run() {

    }
    public synchronized void runGPUEncoder() {
        try {
            createVideoFormat();
            createVideoCodec();
            if ( mAudioFeatureActive ) {
                createAudioFormat();
                createAudioCodec();
            }
            createMuxer();
            runEncoderLoop();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /****************************************************************************
     *  prepare() - Starts the show by settings up the preview window and the callbacks
     *  It then starts the video and audio encoding process
     ****************************************************************************/
    public synchronized  void prepare( ) {
        reportMemoryUsage();
    }

    /**********************************************************
     * release() the encoder which removes the encoder
     **********************************************************/
    public synchronized void release() {
        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
        if ( mAudioFeatureActive ) {
            if (mAudioEncoder != null) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
                mAudioEncoder = null;
            }
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    public synchronized void  reportMemoryUsage() {
        mFreeMegs = (int) (Runtime.getRuntime().freeMemory() - Debug.getNativeHeapFreeSize());
        String freeMegsString = String.format(" - Free Memory  %d MB", mFreeMegs);
        Log.d(TAG, "---------------------------------------------------");
        Log.d(TAG, "Memory free to be used in this app in megs is: " + freeMegsString);
        Log.d(TAG, "---------------------------------------------------");
    }


    public synchronized MediaCodec getCodec() {
        return mCodec;
    }

    public synchronized int getColorFormat() {
        return mColorFormat;
    }

    /*******************************************************************
     * setupClock() record the time before the encoder loop is entered
     *******************************************************************/
    private synchronized void setupClock() {
        long bootTime = SystemClock.elapsedRealtime();
    }

    public synchronized void createVideoFormat() {
        // creat the codec first because you need some info
        try {
            mCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaCodecInfo codecInfo  = mCodec.getCodecInfo();
        Log.w(TAG, "Codec info Name :"     + codecInfo.getName());
        Log.w(TAG, "Codec info Encoder? :" + codecInfo.isEncoder());
        Log.d(TAG, "Encoding width,height passed to the decoder is: "
                + String.valueOf(mEncodingWidth) + "," + String.valueOf(mEncodingHeight));

        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        mFormat =  MediaFormat.createVideoFormat(MIME_TYPE, mEncodingWidth, mEncodingHeight);
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE );
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE,  FRAME_RATE);
        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
    }

    /*******************************************************************
    * createVideoCodec() creates the video codec which is H264 based
    ******************************************************************/
    public synchronized void createVideoCodec() {
        try {
            Log.w(TAG, "----->createVideoCodec()<-----");
            mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.start();
            mBufferInfo = new BufferInfo();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error in creating video codec failed configuration.");
        }
        Log.w(TAG, "----->end createVideoCodec()<-----");
     }

    /********************************************************************************
     * Create a MediaMuxer.  We can't add the video track and start() the muxer here,
     * because our MediaFormat doesn't have the Magic Goodies.  These can only be
     * obtained from the encoder after it has started processing data.
     **********************************************************************************/
    @SuppressWarnings("all")
    private synchronized void createMuxer() {
        Log.d(TAG, "--->createMuxer()");
        if ( isExternalStorageWritable()) {
            File encodedFile = new File(OUTPUT_FILENAME_DIR, "/movies/EncodedAV" + "-" + mEncodingWidth + "x" + mEncodingHeight + ".mp4");
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
            mMuxerStarted = false;
        }
    }

    private synchronized void releaseMuxer() {
        if(mMuxer!=null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    public synchronized void startMuxer() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            Log.e(TAG, "Encoder status: Something wrong, format changed twice");
        }
        if (mNewFormat != null) {
            mVideoTrackIndex = mMuxer.addTrack(mNewFormat);
            if ( mAudioFeatureActive ) {
                mAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
            }
            mMuxer.start();
            mMuxerStarted = true;
            Log.w(TAG, "VideoTrackIndex is: " + mVideoTrackIndex);
            Log.w(TAG, "AudioTrackIndex is: " + mAudioTrackIndex);
        } else {
            Log.e(TAG, "mNewFormat is null in startMuxer()");
        }
        Log.d(TAG, "-------------------------------------------------------------");
        Log.d(TAG, "Muxer started!");
        Log.d(TAG, "-------------------------------------------------------------");
    }

    public synchronized void createAudioFormat() {
        if ( mAudioFeatureActive ) {
            mAudioFormat = new MediaFormat();
            mAudioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
            mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        }
    }

    public synchronized void createAudioCodec() {
        if (mAudioFeatureActive ) {
            try {
                mAudioEncoder = MediaCodec.createEncoderByType(mAudioCodecName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAudioBufferInfo = new MediaCodec.BufferInfo();
            try {
                mAudioEncoder.configure(mAudioFormat,
                        null /* surface */,
                        null /* crypto */,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (IllegalStateException e) {
                Log.e(TAG, "codec '" + mAudioCodecName + "' failed configuration.");
            }
            mAudioEncoder.start();
        }
    }


    public synchronized  void runEncoderLoop() {
        // most of the time the encoder sits in this loop
        // encoding frames until there is no more left
        // currently it is encoding faster than I can feed it
        Log.w(TAG, "--->Start to run encoders<----");
        if (mSharedMemFile == null) {
            Log.e(TAG, "SharedMemory file is null in runEncodedLoop!");
            return;
        }
        try {
            mEncodingStarted = true;
            int  framecount = mSharedMemFile.getFrameCount();

            Log.d(TAG, "video frames stored and waiting to be encoded: " + mSharedMemFile.getFrameCount());
            for (int frame = 0; frame < framecount; frame ++) {
                encodeVideoFromBuffer(frame, mCodec, mColorFormat);
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, e.toString());
        } finally {
            Log.w(TAG, "Release the encodwers and the shared memory!!!!!");
            try {
                release();
                mRecorder.playSound(MP4_FILE_WRITTEN_SOUND);
                mRecorder.uploadFile();
                Log.w(TAG, "--->All done!!!!!!!!");
            } catch (IllegalStateException ex) {
                Log.e(TAG, ex.toString());
            }
        }
    }

    /******************************************************************
     * Encode from buffer rather than a surface
     * @param frameNum = the frame to be encoded
     ******************************************************************/
    @SuppressWarnings("all")
    public synchronized void encodeVideoFromBuffer(int frameNum, MediaCodec codec, int colorFormat ) {
        if (mLastTrackProcessed)
            return;
        Log.w(TAG, "Encoding video into H264!");
        MediaCodec encoder =      codec;
        int  encoderColorFormat = colorFormat;

        // Loop until the output side is done.
        boolean inputDone = false;
        boolean encoderDone = false;
        boolean outputDone = false;
        final int TIMEOUT_USEC = 10000;
        mVideoFrameEncoded++;
        // mVideoTime = mVideoFrameEncoded * 32; // 30 frame a second

        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        int checkIndex = 0;
        int badFrames = 0;
        // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
        // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
        // of algebra and assuming that stride==width and sliceHeight==height yields:
        byte[] frameData = null;
        byte[] videoFrame;
        try {
            frameData = new byte[mEncodingWidth * mEncodingHeight * 3 / 2];
            videoFrame = new byte[frameData.length];
            if (!mSharedMemFile.isEmpty() ) {
                mSharedMemFile.getNextFrame(frameNum, videoFrame);
            }
           // color correct it
           System.arraycopy(videoFrame, 0, frameData, 0, videoFrame.length);

        } catch (OutOfMemoryError e) {
            Log.e(TAG, e.toString());
        } finally {

            // release it
            videoFrame = null;
        }
        if (!inputDone) {
            int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufIndex >= 0) {
                if (mSharedMemFile.isLastFrame()) {
                    // Send an empty frame with the end-of-stream flag set.  If we set EOS
                    // on a frame with data, that frame data will be ignored, and the
                    // output will be short one frame.
                    mVideoTime = computePresentationTime(frameNum);
                    encoder.queueInputBuffer(inputBufIndex, 0, 0, mVideoTime,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                    Log.w(TAG, "sent input EOS (with zero-length frame)");
                } else {
                    ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                    // the buffer should be sized to hold one full frame
                    if (frameData != null && inputBuf.capacity() >= frameData.length) {
                        Log.w(TAG, "buffer resize to hold correct size");
                    } else{
                        Log.e(TAG, "buffer not correct size to fit a frame");
                    }
                    inputBuf.clear();
                    inputBuf.put(frameData);
                    mVideoTime = computePresentationTime(frameNum);
                    if (frameData != null) {
                        encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, mVideoTime, 0);
                    }
                    Log.w(TAG, "submitted frame : time  " + mVideoFrameEncoded +  " : " + mVideoTime + " -> to hardware encoder");
                }
            } else {
                // either all in use, or we timed out during initial setup
                Log.d(TAG, "input buffer not available");
            }
        }
        long rawSize = 0;
        long encodedSize = 0;

        if (!encoderDone) {
            int encoderStatus = encoder.dequeueOutputBuffer(mBufferInfo , TIMEOUT_USEC);
            switch(encoderStatus) {
                case  MediaCodec.INFO_TRY_AGAIN_LATER:
                    // no output available yet
                    Log.d(TAG, "-------------------------------------------------------------");
                    Log.d(TAG, "Encoder status: no output from encoder available");
                    Log.d(TAG, "-------------------------------------------------------------");
                    break;
                case  MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    Log.d(TAG, "-------------------------------------------------------------");
                    Log.d(TAG, "Encoder status: encoder output buffers changed");
                    Log.d(TAG, "-------------------------------------------------------------");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    // not expected for an encoder
                    mNewFormat = encoder.getOutputFormat();
                    Log.d(TAG, "-------------------------------------------------------------");
                    Log.d(TAG, "Encoder status: encoder output format changed: " + mNewFormat.toString());
                    Log.d(TAG, "-------------------------------------------------------------");
                    startMuxer();
                    // reduce the encoded video tracks
                    mVideoFrameEncoded = 0;
                   break;
                default:
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    // Codec config info.  Only expected on first packet.  One way to
                    // handle this is to manually stuff the data into the MediaFormat
                    // and pass that to configure().
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        mBufferInfo.size = 0;
                    }
                    if (encodedData == null) {
                        Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                        return;
                    } else  if ( mVideoFrameEncoded  == 1) {
                        Log.d(TAG, "-----------------------------------");
                        Log.d(TAG, "Setting csd-0 on first track");
                        Log.d(TAG, "-----------------------------------");
                        mFormat.setByteBuffer("csd-0", encodedData);
                    }
                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    if (mBufferInfo.size != 0) {
                        if (encodedData != null && mBufferInfo != null) {
                            encodedData.position(mBufferInfo.offset);
                            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                            encodedSize += mBufferInfo.size;
                            Log.w(TAG, "---->Writing Video data using MediaMuxer");
                            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
                        }
                        if (  mAudioFeatureActive ) {
                            encodeAudio();
                            // the first frames are configured and are skipped check for that
                            if (mCurrentEncodedAudioData != null) {
                                Log.w(TAG, "---->Writing Audio data using MediaMuxer");
                                ByteBuffer audioData = ByteBuffer.wrap(mCurrentEncodedAudioData);
                                mMuxer.writeSampleData(mAudioTrackIndex, audioData, mBufferInfo);
                            }
                            mCurrentEncodedAudioData = null;
                        }
                    }
                    // now release the encoder buffer so the MediaCodec can reuse
                     encoder.releaseOutputBuffer(encoderStatus, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "output EOS");
                        mLastTrackProcessed = true;
                    }
                } // END OF ENODER STATUS > 0
        }     // EMD OF ENCODER LOOP
    }

    @SuppressWarnings("all")
    public synchronized void encodeAudio() {
        if (!mAudioFeatureActive ) {
            return;
        }

        mAudioFrame++;
        // not yet encoded.
        ByteBuffer savedAudioBytes = mApp.pullAudioData();
        byte[] audioBytes =  new byte[savedAudioBytes.capacity()];
        System.arraycopy(savedAudioBytes.array(), 0, audioBytes, 0, audioBytes.length);

        Log.w(TAG, "Encoding audio frame " + mAudioFrame + " into AAC!");
        ByteBuffer[] codecInputBuffers  = mAudioEncoder.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = mAudioEncoder.getOutputBuffers();
        int numBytesSubmitted = 0;
        boolean doneSubmittingInput = false;
        int numBytesDequeued = 0;
        int index;
        if (!doneSubmittingInput) {
            index = mAudioEncoder.dequeueInputBuffer(kTimeoutUs /* timeoutUs */);
            if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (numBytesSubmitted >= kNumInputBytes) {
                    mAudioEncoder.queueInputBuffer(
                            index,
                            0 /* offset */,
                            0 /* size */,
                            0 /* timeUs */,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    Log.d(TAG, "queued input EOS.");
                    doneSubmittingInput = true;
                } else {
                    int size = queueInputBuffer(mAudioEncoder, codecInputBuffers, index, audioBytes);
                    numBytesSubmitted += size;
                    Log.d(TAG, "queued " + size + " bytes of input data.");
                }
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            index = mAudioEncoder.dequeueOutputBuffer(info, kTimeoutUs /* timeoutUs */);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "AUDIO Info try again later!!");
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "encoder output format changed:  Added track index: " + mAudioTrackIndex);
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = mAudioEncoder.getOutputBuffers();
            } else {
                ByteBuffer encodedData = codecOutputBuffers[index];
                if (encodedData == null) {
                    Log.e(TAG, "encoderOutputBuffer " + index + " was null in encoding audio!!");
                    return;
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    info.size = 0;
                }
                // Copy the converted audio data
                mCurrentEncodedAudioData = new byte[info.size];
                System.arraycopy(encodedData , 0, mCurrentEncodedAudioData,0, mCurrentEncodedAudioData.length);
                numBytesDequeued += info.size;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.w(TAG, "dequeued output EOS.");
                }
                Log.w(TAG, "dequeued " + info.size + " bytes of output data.");
            }
        }
        Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
                    + "dequeued " + numBytesDequeued + " bytes.");
        int sampleRate   = mAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = mAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int inBitrate    = sampleRate * channelCount * 16;  // bit/sec
        int outBitrate   = mAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        float desiredRatio = (float)outBitrate / (float)inBitrate;
        float actualRatio  = (float)numBytesDequeued / (float)numBytesSubmitted;
        if (actualRatio < 0.9 * desiredRatio || actualRatio > 1.1 * desiredRatio) {
            Log.w(TAG, "desiredRatio = " + desiredRatio
                    + ", actualRatio = " + actualRatio);
        }
    }

    private synchronized int queueInputBuffer( MediaCodec codec, ByteBuffer[] inputBuffers, int index, byte[] audioBuffer) {
        if ( !mAudioFeatureActive ) {
            return -1;
        }
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
        codec.queueInputBuffer(index, 0 , size, mVideoTime , 0);
        return size;
    }

    private synchronized void dequeueOutputBuffer(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        if (mAudioFeatureActive) {
            codec.releaseOutputBuffer(index, false);
        }
    }

   /*************************************************************************************
   * Returns the first codec capable of encoding the specified MIME type, or null if no
   * match was found.
   *************************************************************************************/
   @SuppressWarnings("deprecation")
   private static MediaCodecInfo selectCodec(String mimeType) {
            int numCodecs = MediaCodecList.getCodecCount();
           for (int i = 0; i < numCodecs; i++) {
               MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
               String[] types = codecInfo.getSupportedTypes();
                for (String codecType : types) {
                    if (codecType.equalsIgnoreCase(mimeType)) {
                        return codecInfo;
                    }
                }
            }
            return null;
      }

     /****************************************************************************************
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     ****************************************************************************************/
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
          MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
          // first print them out
          for (int i = 0; i < capabilities.colorFormats.length; i++) {
              int colorFormat = capabilities.colorFormats[i];
              Log.w(TAG, "Color format found is :" + colorFormat);
          }
          for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
              if (isRecognizedFormat(colorFormat)) {
                   return colorFormat;
               }
           }
           Log.e(TAG,"couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
           return 0;   // not reached
       }

      /**********************************************************************************************
      * @return - true if this is a color format that this test code understands (i.e. we know how
      * to read and generate frames in this format).
      ***********************************************************************************************/
       private static boolean isRecognizedFormat(int colorFormat) {
                boolean flag = false;
            switch (colorFormat) {
               // these are the formats we know how to handle for this test
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                         Log.d(TAG, "Color format found: " + "COLOR_FormatYUV420Planar");
                         flag = true;
                         break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                        Log.d(TAG, "Color format found: " + "COLOR_FormatYUV420PackedPlanar");
                        flag = true;
                        break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                        Log.d(TAG, "Color format found: " + "COLOR_FormatYUV420SemiPlanar");
                        flag = true;
                        break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                        Log.d(TAG, "Color format found: " + "COLOR_FormatYUV420PackedSemiPlanar");
                        flag = true;
                        break;
                case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                        Log.d(TAG, "Color format found: " + "COLOR_TI_FormatYUV420PackedSemiPlanar");
                        flag = true;
                default:
                        flag = false;
           }
            return flag;
     }

    /****************************************************************************************
    * Returns true if the specified color format is semi-planar YUV.  Throws an exception
    * if the color format is not recognized (e.g. not YUV).
     ***************************************************************************************/
     private static boolean isSemiPlanarYUV(int colorFormat) {
           switch (colorFormat) {
             case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
              case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                     return false;
              case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
              case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
              case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                  return true;
              default:
                  throw new RuntimeException("unknown format " + colorFormat);
           }
     }

    /*************************************************
    * Start the video codec and create the buffers
    **************************************************/
	private void startCodec() {
	  mCodec.start();
 	}

    /******************************************************************************************
    * Returns true if the actual color value is close to the expected color value.  Updates
    * mLargestColorDelta.
    * @param actual actual Color
    * @param expected expected Color
    * @return is the color expected closew to what is displayed?
    *******************************************************************************************/
    @SuppressWarnings("deprecation")
    boolean isColorClose(int actual, int expected) {
	        final int MAX_DELTA = 8;
	        int delta = Math.abs(actual - expected);
	        if (delta > mLargestColorDelta) {
	            mLargestColorDelta = delta;
	        }
        return (delta <= MAX_DELTA);
    }

    /***********************************************************
     * This code will be replaced by Rest Code or socket code
     ***********************************************************/
    @SuppressWarnings("all")
 	private void saveVideoToWebServer() {
		  try {
         		final Long start = System.nanoTime();
         		Time now = new Time();
	      	  	now.setToNow();
			  	final String strDate = now.format2445() + "_" + start;
		   		// Create JPEG
				final Parameters parameters = mCamera.getParameters();
                final Thread writeToWebServer = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String finalPath = dirImages + strDate + "_image.jpg";
                        //Log.d(TAG, "Creating filename :" + finalPath);
                        try {
                            File imageSnapShot = new File(finalPath);
                            imageSnapShot.createNewFile();
                            FileInputStream fis = new FileInputStream(imageSnapShot);
                            //  change frame to JPEG and write to outputstream
                            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            int quality = 100;
                            int imageFormat = parameters.getPreviewFormat();
                            // this format is for sure with any camera
                            if (imageFormat != ImageFormat.NV21)
                                return;
                            int previewSizeWidth = parameters.getPreviewSize().width;
                            int previewSizeHeight = parameters.getPreviewSize().height;
                            Rect previewSize = new Rect(0, 0, previewSizeWidth, previewSizeHeight);
                            YuvImage image = new YuvImage(mVvideoFrameData, ImageFormat.NV21, previewSizeWidth, previewSizeHeight, null /* strides */);

                            image.compressToJpeg(previewSize, quality, bos);
                            bos.flush();
                            bos.close();
                            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                            String url = "http://10.19.73.148:9000/images/";
                            InputStreamEntity reqEntity = new InputStreamEntity(bis, -1);
                            HttpClient httpclient = new DefaultHttpClient();
                            HttpPost httppost = new HttpPost(url);
                            reqEntity.setContentType("binary/octet-stream");
                            reqEntity.setChunked(true); // Send in multiple parts if needed
                            httppost.setEntity(reqEntity);
                            HttpResponse response = null;
                            response = httpclient.execute(httppost);
                        } catch (IOException ex) {
                            Log.d(TAG, ex.getMessage());
                        }
                    }
                    });
                    writeToWebServer.start();
                    writeToWebServer.join();

                    long end = System.nanoTime();
                    long elapsedTime = end - start;
                    double seconds = (double) elapsedTime / 1000000000.0;
                    Log.d(TAG, "Time elasped and image file written to Network: " + seconds + " [" + strDate + "]");
       		 } catch (Exception e) {
						  Log.e(TAG, e.getMessage());
		    }
	}

    /*************************************************************
    * Checks if external storage is available for read and write
    * @return can I write to a sd card
    **************************************************************/
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /*************************************************************
    /* Checks if external storage is available to at least read
    *************************************************************/
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }
    /*****************************************************************
     * Generates the presentation time for frame N, in microseconds.
     * @param frameIndex the index of teh frame
     * @return long  the new time
     ****************************************************************/
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}