package com.constantinnovationsinc.livemultimedia.encoders;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.GLES20;
import android.os.Debug;
import android.os.Environment;
import android.os.SystemClock;
import android.text.format.Time;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.khronos.opengles.GL10;

import com.constantinnovationsinc.livemultimedia.activities.HWBroadcastingActivity;
import com.constantinnovationsinc.livemultimedia.previews.FrameCatcher;
import com.constantinnovationsinc.livemultimedia.previews.VideoPreview;
import com.constantinnovationsinc.livemultimedia.surfaces.InputSurface;
import com.constantinnovationsinc.livemultimedia.surfaces.OutputSurface;

/**********************************************************************************************
 * This calls handles the encoding of the video using the hardware encoder in the GPU
 * The code that contains the preview window and camera will be moved to separate classes soon
 * The audio code will be moved as well, it will be changed over to MediaCodec instead
 * af of using MediarRecorder
 *********************************************************************************************/

public class EncodingThread implements Runnable {
    private static final String TAG = EncodingThread.class.getName();
    private static final int ENCODING_WIDTH = 640;
    private static final int ENCODING_HEIGHT = 480;
    private static final int BITRATE = 6000000;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 2;
    private static final boolean WORK_AROUND_BUGS = false;  // avoid fatal codec bugs
    // movie length, in frames
    private static final int NUM_FRAMES = 330;               // 9 seconds of video
    private static int FRAME_RATE = 30;
	private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
	private static final String DEBUG_FILE_NAME_BASE = "/sdcard/media/";
    private static final String  MIME_TYPE = "video/avc";
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    private String dirImages = null;
    private byte[] mVvideoFrameData = null;
    private long bootTime = -1;
    private long realTime = -1;
    private int mBitRate  = -1;
    private int mEncodingWidth = -1;
    private int mEncodingHeight = -1;
    private long mPreviewWidth = -1;
    private long mPreviewHeight = -1;
    private int mImageFormat = -1;

    // used in memory calculation
    private int mFreeMegs = -1;
    private int  mUsedMegs = -1;
    // largest color component delta seen (i.e. actual vs. expected)
    private int mLargestColorDelta = -1;
    private int mTrackIndex = -1;
    private long mVideoFrameEncoded = -1;
    private long mVideoTime = -1;
    private boolean mMuxerStarted = false;
    private boolean mLastTrackProcessed = false;
    /* -------private complex types --------- */
    private Context mContext = null;
    private File hscImageDir = null;
    private MediaMuxer mMuxer = null;
    private FileOutputStream outputStream = null;
    private MediaRecorder mRec  = null;
    private Thread thread = null;
    private MediaCodec mCodec = null;
    private BufferInfo mInfo = null;
    private ByteBuffer[] inputBuffers  = null;
    private ByteBuffer[] outputBuffers = null;
    // allocate one of these up front so we don't need to do it every time
    private BufferInfo mBufferInfo = null;
    private MediaFormat mFormat    = null;
    /* -------------------------------------*/

    /* -------public complex types --------- */
    public  HWEncoder hwEncoder = null;
    public  HWEncoder mStreamer = null;
    public  VideoPreview mSurfaceView = null;
    // holds all the frames, as FrameCatcher loads, this class feed to encoder
    // replace with memory mapped file instead of a static arrayList
    public  static volatile ArrayList<byte[] > mVideoFrameList = new ArrayList<byte[]>();
    public  Camera mCamera = null;
    /* -------------------------------------*/

    /*********************************************************************
     * Constructor
     * @param context - the context associated with this encoding thread
4     *********************************************************************/
    EncodingThread(Context context) {
    		mContext = context;
    }

    /*********************************************************************
     * Start the encoding thread
     * @return context - the context associated with this encoding thread
     *********************************************************************/
    public void start() throws IOException {
    	 if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }
    
 	@Override
	public void run() {
 		startEncoding();
	}

    /****************************************************************************
     *  Start the show by settings up the preview window and the callbacks
     *  It then starts the video and audio encoding process
    ****************************************************************************/
    public synchronized  void startEncoding( ) {
		mFreeMegs = (int) (Runtime.getRuntime().freeMemory() - Debug.getNativeHeapFreeSize());
        String freeMegsString = String.format(" - Free Memory  %d MB", mFreeMegs);
        Log.d(TAG, "---------------------------------------------------");
        Log.d(TAG, "Memory free to be used in this app in megs is: " + freeMegsString);
        Log.d(TAG, "---------------------------------------------------");

        // this is currently using 21 megs of memory
    	setParameters( ENCODING_WIDTH, ENCODING_HEIGHT, BITRATE);
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
 		Parameters parameters = mCamera.getParameters();

 		// set the preview size
 		queryPreviewSettings(parameters);
  		adjustPreviewSize(parameters);

  		// set the frame rate and update the camera parameters
  		setPreviewFrameRate(parameters, FRAME_RATE);
  		adjustCamera(parameters);
  		mCamera.setParameters(parameters);

  		// set up the callback to capture the video frames
  		setupVideoFrameCallback();
      	if (!startVideoPreview()) {
        		Log.e(TAG, "Preview Display problem, exit.....");
        		return;
       	}

       	// the recording will end after 10 seconds
       	setupClock();
       	startAudioRecorder(hscImageDir);
        startTimer();

       	// encode
        startEncodingInHardware();
      	// attach streaming code here
   	}

    /************************************************************
    * ensure the view is in landscape
    * @param parameters
    ************************************************************/
    private void adjustCamera(Parameters parameters) {
        if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            mCamera.setDisplayOrientation(0);
        }
    }

    /**********************************************************
    * record teh time before the encoder loop is entered
    **********************************************************/
    private void setupClock() {
            bootTime = SystemClock.elapsedRealtime();
   	}

    /**********************************************************
     * start the preview so you cna begin capturing frames
     * @return the preview started or not
     **********************************************************/
	private Boolean startVideoPreview() {
     	   try {
       		   if (mSurfaceView == null) {
     			   	Log.e(TAG,  "SurfaceView is null, unable to set setPreviewDisplay!");
     			   	return false;
     		   }
      	   	   if (mSurfaceView.getHolder() == null) {
     		   		 	Log.e(TAG,  "SurfaceView, SurfaceHolder is null, previewDisplay not setup!");
     		 		   	return false;
      		   }
  			  mCamera.setPreviewDisplay( mSurfaceView.getHolder());
		 	} catch (IOException e) {
			 	Log.e(TAG ,  e.getMessage());
		 	}
     		mCamera.startPreview();
     		return true;
 	}
 
	/***********************************************************
	* Sets the desired frame size and bit rate.
    * @param width
    * @param height
    * @param bitRate
	**********************************************************/
	private synchronized void setParameters(int width, int height, int bitRate) {
	               if ((width % 16) != 0 || (height % 16) != 0) {
	                   Log.w(TAG, "WARNING: width or height not multiple of 16");
	               }
	         mEncodingWidth = width;
	         mEncodingHeight = height;
	         mBitRate = bitRate;
	}

    /************************************************************
    * create the setup of teh hardware decoder by creating the
    * MediaCodex and MediaMuxer
    ************************************************************/
	private void startEncodingInHardware() {
			try {
			     MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
		          if (codecInfo == null) {
		                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
		                return;
		          }
		         Log.d(TAG, "found codec: " + codecInfo.getName());
				 int colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
				 Log.d(TAG, "Color format found: " + colorFormat);
	
				 mFormat =  MediaFormat.createVideoFormat(MIME_TYPE, mEncodingWidth, mEncodingHeight);
	             mFormat.setInteger(MediaFormat.KEY_BIT_RATE,  mBitRate );
	 	         mFormat.setInteger(MediaFormat.KEY_FRAME_RATE,  FRAME_RATE);
	 	         mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
	 	         mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
	 	    
	 	         mCodec = MediaCodec.createByCodecName(codecInfo.getName());
	 		     mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
	 	         mCodec.start();
	 	         
	 	         mBufferInfo = new BufferInfo();

                createMediaMuxer();
                // delay the encoder because its encodes faster than I can fill it
                // wait until 50 frames are filled
                // Replace this code with Memory mapped file code and a timer
                while (mVideoFrameList.size()  < 50)  {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getLocalizedMessage());
                        }
                }
                // most of the time the encoder sits in this loop
                // encoding frames until there is no more left
                // currently it is encoding faster than I can feed it
                while (!mVideoFrameList.isEmpty()) {
                        Log.d(TAG, "video frames stored and waiting to be encoded: " + mVideoFrameList.size());
                        encodeVideoFromBuffer(mCodec, colorFormat);
                }
            } catch (IOException ioe) {
                     throw new RuntimeException("MediaMuxer creation failed", ioe);
            } catch (OutOfMemoryError error) {
            	 		Log.e(TAG, "Out of Memory when running Encoder!");
            }
			finally {
		    	 	Log.d(TAG, "releasing codecs");
	            if (mMuxer != null) {
	                mMuxer.stop();
	                mMuxer.release();
	                mMuxer = null;
	            }
	            if (mCodec != null) {
	                mCodec.stop();
	                mCodec.release();
	            }
	            // free memory
	            mVideoFrameList.clear();
	            if (mCamera != null) {
	            		mCamera.addCallbackBuffer(null);
	            		mCamera.stopPreview();
	            		mCamera.release();
	            		mCamera = null;
	  	      }
	            // now close the child activity
	            ((HWBroadcastingActivity)mContext).finish();
			}   
	 }

    /**********************************************************************************
    * Create a MediaMuxer.  We can't add the video track and start() the muxer here,
    * because our MediaFormat doesn't have the Magic Goodies.  These can only be
    * obtained from the encoder after it has started processing data.
    ***********************************************************************************/
    private void createMediaMuxer() {
        File encodedFile = new File(DEBUG_FILE_NAME_BASE,  "EncodedVideo" + mEncodingWidth + "x" + mEncodingHeight + ".mp4");
        if (encodedFile.exists()) {
            encodedFile.delete();
        }
        String outputPath = encodedFile.toString();
        int format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
        try {
            mMuxer = new MediaMuxer(outputPath , format);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        mTrackIndex = -1;
        mMuxerStarted = false;
    }

     /******************************************************************
     * Encode from buffer rather than a surface
     * @param encoder - the mediacodec, currently only H264 is supported
     * @param encoderColorFormat - color format
     ******************************************************************/
	 private void encodeVideoFromBuffer(MediaCodec encoder, int encoderColorFormat) {
	
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
                   
                   // estimated memory usage
                   mUsedMegs = (int) (Debug.getNativeHeapAllocatedSize() / 1048576L);
                   String usedMegsString = String.format(" - Memory Used: %d MB", mUsedMegs);
                   Log.d(TAG, "Memory used in megs is: " + usedMegsString);
                   // try and predict if the memory allocation will fail
                   int memoryLeft = mFreeMegs - mUsedMegs;
                   if (memoryLeft <= 2) {
                	      Log.e(TAG,  "Memory left in megs is roughly: " + memoryLeft);
                   }
                		   
                  byte[] frameData = new byte[mEncodingWidth * mEncodingHeight * 3 / 2];
                  byte[] 	videoFrame = new byte[frameData.length];
                  if (!mVideoFrameList.isEmpty() ) {
                	  		 	videoFrame = mVideoFrameList.remove(0);
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
                           if (mVideoFrameList.isEmpty()) {
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
                	   int encoderStatus = encoder.dequeueOutputBuffer(mBufferInfo , TIMEOUT_USEC);
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
                                mTrackIndex = mMuxer.addTrack(newFormat);
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
                              if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                // The codec config data was pulled out and fed to the muxer when we got
                                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                               Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                               mBufferInfo.size = 0;
                              }
                    	      if (encodedData == null) {
                                  Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                              } else  if ( mVideoFrameEncoded  == 1) {
                                    Log.d(TAG, "-----------------------------------");
                                    Log.d(TAG, "Setting csd-0 on first track");
                                    Log.d(TAG, "-----------------------------------");
                                    mFormat.setByteBuffer("csd-0", encodedData);
                              }
                 	          // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                          	 if (mBufferInfo.size != 0) {
                          		  encodedData.position( mBufferInfo.offset);
                          	      encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                          	      encodedSize += mBufferInfo.size;
                          	      Log.d(TAG, "Writing Video data using MediaMuxer");
                                   mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                                   Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                                   // pass encoded track to streamer
                                   //mStreamer.writeVideoChunk( encodedData.array() );
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
		
	   /*************************************************************************************
       * Generates data for frame N into the supplied buffer.  We have an 8-frame animation
       * sequence that wraps around.  It looks like this:
       * <pre>
       *   0 1 2 3
       *   7 6 5 4
       * </pre>
       * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
       * @param frameIndex
       * @param colorFormat
       * @param frameData
       * @return none
       **************************************************************************************/
       private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {
             final int HALF_WIDTH = mEncodingWidth / 2;
             boolean semiPlanar = isSemiPlanarYUV(colorFormat);
      
             // Set to zero.  In YUV this is a dull green.
             Arrays.fill(frameData, (byte) 0);
     
             int startX, startY, countX, countY;
      
             frameIndex %= 8;
              //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
             if (frameIndex < 4) {
                  startX = frameIndex * (mEncodingWidth / 4);
                 startY = 0;
              } else {
                 startX = (7 - frameIndex) * (mEncodingWidth / 4);
                  startY = mEncodingHeight / 2;
              }
      
              for (int y = startY + (mEncodingHeight/2) - 1; y >= startY; --y) {
                 for (int x = startX + (mEncodingWidth/4) - 1; x >= startX; --x) {
                      if (semiPlanar) {
                         // full-size Y, followed by UV pairs at half resolution
                          // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                         // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                         //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                         frameData[y * mEncodingWidth + x] = (byte) TEST_Y;
                         if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                              frameData[mEncodingWidth*mEncodingHeight + y * HALF_WIDTH + x] = (byte) TEST_U;
                              frameData[mEncodingWidth*mEncodingHeight + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                         }
                      } else {
                         // full-size Y, followed by quarter-size U and quarter-size V
                         // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                          // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
      							frameData[y * mEncodingWidth + x] = (byte) TEST_Y;
      							 if ((x & 0x01) == 0 && (y & 0x01) == 0) {
      								 frameData[mEncodingWidth*mEncodingHeight + (y/2) * HALF_WIDTH + (x/2)] = (byte) TEST_U;
      								 frameData[mEncodingWidth*mEncodingHeight + HALF_WIDTH * (mEncodingHeight / 2) +
                                        (y/2) * HALF_WIDTH + (x/2)] = (byte) TEST_V;
                          }
                      }
                  }
              }
          }
		
		
	   /*************************************************************************************
	   * Returns the first codec capable of encoding the specified MIME type, or null if no
	   * match was found.
	   *************************************************************************************/
	   private static MediaCodecInfo selectCodec(String mimeType) {
	            int numCodecs = MediaCodecList.getCodecCount();
	           for (int i = 0; i < numCodecs; i++) {
	                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
	    
	               if (!codecInfo.isEncoder()) {
	                    continue;
	                }
	    
	                String[] types = codecInfo.getSupportedTypes();
	                for (int j = 0; j < types.length; j++) {
	                    if (types[j].equalsIgnoreCase(mimeType)) {
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
			  inputBuffers = mCodec.getInputBuffers();
			  outputBuffers = mCodec.getOutputBuffers();
	}

    /**********************************************************
     * capture video frame one by one from the preview window
     * setup teh buffer to hold the images
     **********************************************************/
    private void  	setupVideoFrameCallback() {
            FrameCatcher catcher = new FrameCatcher( mPreviewWidth,  mPreviewHeight);
            long bufferSize = 0;
            bufferSize = mPreviewWidth * mPreviewHeight  * ImageFormat.getBitsPerPixel(mImageFormat) / 8;
            long sizeWeShouldHave = (mPreviewWidth * 	mPreviewHeight  * 3 / 2);
            Log.d(TAG, "BufferSize for videodata is: " +   bufferSize );
                Log.d(TAG, "Buffer size we should have is: " +  sizeWeShouldHave  );
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.setPreviewCallbackWithBuffer(catcher);
           for (int i = 0; i < NUM_CAMERA_PREVIEW_BUFFERS; i++) {
    		           byte [] cameraBuffer = new byte[(int)bufferSize];
    		            mCamera.addCallbackBuffer(cameraBuffer);
    	   }
     }

    /******************************************************************************************
     *  The preview window can supprt different image formats depending on the camera make
     *  Almost all support NV21 and JPEG
     * @param parameters
     ****************************************************************************************/
     private void    queryPreviewSettings(Parameters parameters) {
               List<int[]>  supportedFps = parameters.getSupportedPreviewFpsRange();
               for (int[] item : supportedFps) {
                        Log.d(TAG, "Mix preview frame rate supported: " + item[ Parameters.PREVIEW_FPS_MIN_INDEX]/ 1000  );
                        Log.d(TAG, "Max preview frame rate supported: " + item[ Parameters.PREVIEW_FPS_MAX_INDEX]/ 1000  );
                }
                List<Integer> formats = parameters.getSupportedPreviewFormats();
                for (Integer format : formats) {
                        if (format == ImageFormat.JPEG)  {
                            Log.d(TAG, "This camera supports JPEG format in preview");
                        }
                        if (format == ImageFormat.NV16)  {
                            Log.d(TAG, "This camera supports NV16 format in preview");
                        }
                        if (format == ImageFormat.NV21)  {
                            Log.d(TAG, "This camera supports NV21 format in preview");
                        }
                        if (format == ImageFormat.RGB_565)  {
                            Log.d(TAG, "This camera supports RGB_5645 format in preview");
                        }
                        if (format == ImageFormat.UNKNOWN)  {
                            Log.e(TAG, "This camera supports UNKNOWN format in preview");
                        }
                        if (format == ImageFormat.YUV_420_888)  {
                            Log.e(TAG, "This camera supports YUV_420_888 format in preview");
                        }
                        if (format == ImageFormat.YUY2)  {
                            Log.e(TAG, "This camera supports YUY2 format in preview");
                        }
                        if (format == ImageFormat.YV12)  {
                            Log.e(TAG, "This camera supports YV12 format in preview");
                        }
                }

               mImageFormat = parameters.getPreviewFormat();
               if (mImageFormat != ImageFormat.NV21) {
                  Log.e(TAG,  "Bad reported image format, wanted NV21 (" + ImageFormat.NV21 +
                          ") got " + mImageFormat);
              }
        }

     /*******************************************************************
     * Change this to the resolution you want to capture and encode to
     * @param parameters camera preview settings
     ******************************************************************/
    private void adjustPreviewSize(Parameters parameters) {
        mPreviewWidth = parameters.getPreviewSize().width;
        mPreviewHeight = parameters.getPictureSize().height;
        Log.d(TAG, "Current preview width and height is: " + mPreviewWidth  + "," + mPreviewHeight);
        List<Size> sizes = parameters.getSupportedPreviewSizes();
         for (Size size : sizes) {
              Log.d(TAG , "Preview sizes supported by this camera is: " + size.width + "x" + size.height);
         }
        mPreviewWidth   = mEncodingWidth;
        mPreviewHeight =  mEncodingHeight;
         Log.d(TAG, "New preview size is: " +  mPreviewWidth  + "x" + mPreviewHeight );
         parameters.setPreviewSize( (int)mPreviewWidth,  (int)mPreviewHeight);
     }

    /*****************************************************************************************************
     * Make sure the preview capture rate is consistent by locking the exposure and white balance rate
     * @param parameters
     * @param frameRate
     ****************************************************************************************************/
     private void setPreviewFrameRate(Parameters parameters, int frameRate) {
         int actualMin = frameRate * 1000;
         int actualMax = actualMin;
         // try to lock the camera settings to get the frame rate we want
          if (parameters.isAutoExposureLockSupported()) {
                parameters.setAutoExposureLock(true);
          }
          if (parameters.isAutoWhiteBalanceLockSupported()) {
                parameters.setAutoWhiteBalanceLock(true);
          }
          parameters.setPreviewFpsRange( actualMin, actualMax ); // for 30 fps
     }

    /**************************************************
     * Replace this with mediaCodec
     * @param audioDir
     *************************************************/
	private void startAudioRecorder(File audioDir ) {
            mRec = new MediaRecorder();
            //mHSCPreview.setCamera(mCamera);
            mRec.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            File audiofile = null;
            try {
            		audiofile = File.createTempFile("live", ".3gp", audioDir);
            } catch(IOException e) {
            		Log.e(TAG, "sdccard error");
            }
            mRec.setOutputFile(audiofile.getAbsolutePath());
            try {
				mRec.prepare();
			} catch (IllegalStateException e) {
				Log.e(TAG, e.getMessage());
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
            mRec.start();
     }

    /************************************************
     * Auto stop the recording after 10 seconds
    *************************************************/
	private void startTimer() {
	       // run for 10 seconds
			final Timer timer = new Timer();
			timer.scheduleAtFixedRate(new TimerTask() {
				 @Override
				    public void run() {
						Log.d(TAG, "Timer ended after approx 10 seconds!!!");
						Log.d(TAG, "Stopping any encoding and recroding");
						timer.cancel(); // 10 seconds up stop the timer
					 	mRec.stop();
					 	mRec.reset();
					 	mRec.release();
				        mCamera.stopPreview();
				        mCamera.release();
				        mCamera = null;
				        mRec = null;
				 }			
			},
			10000, // run in 10 seconds
			1000);
			Log.d(TAG, "Timer started!");
	}
  	 
    /******************************************************************************************
    * Returns true if the actual color value is close to the expected color value.  Updates
    * mLargestColorDelta.
    * @param actual
    * @param expected
    * @return is the color expected closew to what is displayed?
    *******************************************************************************************/
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
 	private void saveVideoToWebServer() {
		  try {
         		final Long start = System.nanoTime();
         		Time now = new Time();
	      	  	now.setToNow();
			  	final String strDate = now.format2445() + "_" + start;
		   		// Create JPEG
				final Parameters parameters = mCamera.getParameters();
				try {
				 		  Thread writeToWebServer = new Thread(new Runnable() {
				            @Override
				            public void run() {
				 		  		String finalPath = dirImages  +  strDate + "_image.jpg";
 	 					  		//Log.d(TAG, "Creating filename :" + finalPath);
 	 					  	  	try {
 	 					  	  	    File imageSnapShot = new File( finalPath );
 		  	   	 				    imageSnapShot.createNewFile();
								    FileInputStream fis = new FileInputStream(imageSnapShot);
	   	 					        //  change frame to JPEG and write to outputstream
 	   	 					  	    final ByteArrayOutputStream bos = new ByteArrayOutputStream( );
 	   	 					  	    int quality = 100;
 	   	 					  	    int imageFormat = parameters.getPreviewFormat();
 	   	 					  	    // this format is for sure with any camera
 	   	 					  	    if ( imageFormat != ImageFormat.NV21)
 	   	 					  		    return;
 	   	 					  	    int  previewSizeWidth  =  parameters.getPreviewSize().width;
 	   	 					  	    int  previewSizeHeight =  parameters.getPreviewSize().height;
 	   	 					  	    Rect previewSize = new Rect(0, 0, previewSizeWidth, previewSizeHeight);
 	   	 					  	    YuvImage image = new YuvImage(mVvideoFrameData,  ImageFormat.NV21, previewSizeWidth ,   previewSizeHeight , null /* strides */);
 	 		
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
				            	} catch (ClientProtocolException e) {
									Log.d(TAG, e.getMessage());
				            	} catch (IOException e) {
									Log.d(TAG, e.getMessage());
				            	}
		   	 				}
						  });
						 writeToWebServer.start();
						 writeToWebServer.join();
						  
						 long end = System.nanoTime();
						 long elapsedTime = end - start;
						 double seconds = (double)elapsedTime / 1000000000.0;
						 Log.d(TAG, "Time elasped and image file written to Network: " + seconds + " [" + strDate + "]");
					  } catch (Exception e) {
						  Log.e(TAG, e.getMessage());
					  }
		  			} finally {
		  			}
	}

    /*****************************************************************
     * Generates the presentation time for frame N, in microseconds.
     * @param frameIndex teh index of teh frame
     * @return long  the new time
     ****************************************************************/
     private static long computePresentationTime(int frameIndex) {
             return 132 + frameIndex * 1000000 / FRAME_RATE;
     }

    /****************************************************************
    * Create a directory to store the images on the device
    *****************************************************************/
 	private void createImageDirectory() {
     // class vars
	   dirImages = Environment.getExternalStorageDirectory() + "/livemultimedia_images/";
	   hscImageDir = new File(dirImages);
 	   if (!hscImageDir.exists()) {
 				Log.d(TAG, "This image dir created: " + dirImages);
 				hscImageDir.mkdirs();
 	   }
 	}

    /*************************************************************
    * Checks if external storage is available for read and write
    * @return can I write to a sd card
    **************************************************************/
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /*************************************************************
    /* Checks if external storage is available to at least read
    *************************************************************/
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
            Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }
    
    /*******************************************
     * Tests editing of a video file with GL.
     ******************************************/
    private void videoEditTest() {
        VideoChunks sourceChunks = new VideoChunks();
        if (!generateVideoFile(sourceChunks)) {
            // No AVC codec?  Fail silently.
            return;
        }
        if (DEBUG_SAVE_FILE) {
            // Save a copy to a file.  We call it ".mp4", but it's actually just an elementary
            // stream, so not all video players will know what to do with it.
            String dirName = mContext.getFilesDir().getAbsolutePath();
            String fileName = "vedit1_" + mEncodingWidth + "x" + mEncodingHeight + ".mp4";
            sourceChunks.saveToFile(new File(dirName, fileName));
        }
        VideoChunks destChunks = editVideoFile(sourceChunks);
        if (DEBUG_SAVE_FILE) {
            String dirName = mContext.getFilesDir().getAbsolutePath();
            String fileName = "vedit2_" + mEncodingWidth + "x" + mEncodingHeight + ".mp4";
          destChunks.saveToFile(new File(dirName, fileName));
        }
       checkVideoFile(destChunks);
    }
    
    /*******************************************************************************************
    * Edits a video file, saving the contents to a new file.  This involves decoding and
    * re-encoding, not to mention conversions between YUV and RGB, and so may be lossy.
    * <p>
    * If we recognize the decoded format we can do this in Java code using the ByteBuffer[]
    * output, but it's not practical to support all OEM formats.  By using a SurfaceTexture
    * for output and a Surface for input, we can avoid issues with obscure formats and can
    * use a fragment shader to do transformations.
    * @param inputData
    * @return VideoChunks
    *******************************************************************************************/
    private VideoChunks editVideoFile(VideoChunks inputData) {
        			 Log.d(TAG, "editVideoFile " + mEncodingWidth + "x" + mEncodingHeight);
            VideoChunks outputData = new VideoChunks();
            MediaCodec decoder = null;
            MediaCodec encoder = null;
            InputSurface inputSurface = null;
            OutputSurface outputSurface = null;
    
            try {
               MediaFormat inputFormat = inputData.getMediaFormat();
    
                // Create an encoder format that matches the input format.  (Might be able to just
                // re-use the format used to generate the video, since we want it to be the same.)
               MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, mEncodingWidth, mEncodingHeight);
               outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
               MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
               outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
               inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
               outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
               inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
               outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
               inputFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
    
               outputData.setMediaFormat(outputFormat);
                try {
                    encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                } catch (IOException e) {
                    Log.e(TAG, e.fillInStackTrace().getMessage());
                }
                encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
               inputSurface = new InputSurface(encoder.createInputSurface());

                inputSurface.makeCurrent();
                encoder.start();
    
               // OutputSurface uses the EGL context created by InputSurface.
                try {
                    decoder = MediaCodec.createDecoderByType(MIME_TYPE);
                } catch (IOException e) {
                    Log.e(TAG, e.fillInStackTrace().getLocalizedMessage());
                }
                outputSurface = new OutputSurface();
               //outputSurface.changeFragmentShader(FRAGMENT_SHADER);
               decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
               decoder.start();
    
               inputData.editVideoData(decoder, outputSurface, inputSurface, encoder, outputData);
           } finally {
               Log.d(TAG, "shutting down encoder, decoder");
                if (outputSurface != null) {
                    outputSurface.release();
                }
                if (inputSurface != null) {
                    inputSurface.release();
                }
                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                }
                if (decoder != null) {
                    decoder.stop();
                   decoder.release();
                }
           }
          return outputData;
     }

    /*************************************************************
     * Generates a test video file, saving it as VideoChunks.
     * We generate frames with GL to
     * avoid having to deal with multiple YUV formats.
     * @param  output The VideoChunk that the file is based on
     * @return true on success, false on "soft" failure
     *************************************************************/
    private boolean generateVideoFile(VideoChunks output) {
        Log.d(TAG, "generateVideoFile " + mEncodingWidth + "x" + mEncodingHeight);
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        try {
            MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
            if (codecInfo == null) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
                return false;
            }
             Log.d(TAG, "found codec: " + codecInfo.getName());
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mEncodingWidth, mEncodingHeight);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            Log.d(TAG, "format: " + format);
            output.setMediaFormat(format);
            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties.
            try {
                encoder = MediaCodec.createByCodecName(codecInfo.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();
            generateVideoData(encoder, inputSurface, output);
        } finally {
            if (encoder != null) {
                Log.d(TAG, "releasing encoder");
                encoder.stop();
                encoder.release();
                Log.d(TAG, "released encoder");
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
        return true;
    }


    /****************************************************************************************
     * Checks the video file to see if the contents match our expectations.  We decode the
     * video to a Surface and check the pixels with GL.
     * @param inputData
     ****************************************************************************************/
    private void checkVideoFile(VideoChunks inputData) {
        OutputSurface surface = null;
        MediaCodec decoder = null;
        mLargestColorDelta = -1;
        Log.d(TAG, "checkVideoFile");
        try {
            surface = new OutputSurface(mEncodingWidth, mEncodingHeight);
            MediaFormat format = inputData.getMediaFormat();
            try {
                decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            decoder.configure(format, surface.getSurface(), null, 0);
            decoder.start();
            int badFrames = checkVideoData(inputData, decoder, surface);
            if (badFrames != 0) {
                Log.e(TAG, "Found " + badFrames + " bad frames");
            }
        } finally {
            if (surface != null) {
                surface.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            Log.i(TAG, "Largest color delta: " + mLargestColorDelta);
        }
    }
    
    
    /***************************************************************************
    * Checks the video data.
    * @param inputData - the video chunk to check
    * @param decoder - the decoder to use
    * @param surface-  the output surface to use, should be a video surface
    * @return the number of bad frames
    ****************************************************************************/
    private int checkVideoData(VideoChunks inputData, MediaCodec decoder, OutputSurface surface) {
        final int TIMEOUT_USEC = 1000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();
        int inputChunk = 0;
        int checkIndex = 0;
        int badFrames = 0;
        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            Log.d(TAG, "check loop");
            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (inputChunk == inputData.getNumChunks()) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        Log.d(TAG, "sent input EOS");
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputData.getChunkData(inputChunk, inputBuf);
                        int flags = inputData.getChunkFlags(inputChunk);
                        long time = inputData.getChunkTime(inputChunk);
                        decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                time, flags);
                         Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    inputBuf.position() + " flags=" + flags);
                        inputChunk++;
                    }
                } else {
                    Log.d(TAG, "input buffer not available");
                }
            }
            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    decoderOutputBuffers = decoder.getOutputBuffers();
                    Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    Log.e(TAG,"unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
                    ByteBuffer decodedData = decoderOutputBuffers[decoderStatus];
                     Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                         Log.d(TAG, "output EOS");
                        outputDone = true;
                    }
                    boolean doRender = (info.size != 0);
                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        Log.d(TAG, "awaiting frame " + checkIndex);
                        if (computePresentationTime(checkIndex) ==  info.presentationTimeUs) {
                        			Log.e(TAG, "Wrong time stamp");
                        }			
                        surface.awaitNewImage();
                        surface.drawImage();
                        if (!checkSurfaceFrame(checkIndex++)) {
                            badFrames++;
                        }
                    }
                }
            }
        }
        return badFrames;
    }
    
    /***************************************************************************************
    * Generates video frames, feeds them into the encoder, and writes the output to the
    * VideoChunks instance.
    * @param encoder = the encoder to use for the transformation
    * @param inputSurface - inputSurface of the videoChunks
    * @param output - The VideoChunk to generate of
    **************************************************************************************/
    private void generateVideoData(MediaCodec encoder, InputSurface inputSurface,
                VideoChunks output) {
            final int TIMEOUT_USEC = 10000;
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            BufferInfo info = new BufferInfo();
            int generateIndex = 0;
            int outputCount = 0;
    
            // Loop until the output side is done.
            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                 Log.d(TAG, "gen loop");
    
                // If we're not done submitting frames, generate a new one and submit it.  The
                // eglSwapBuffers call will block if the input is full.
                if (!inputDone) {
                    if (generateIndex == NUM_FRAMES) {
                        // Send an empty frame with the end-of-stream flag set.
                        Log.d(TAG, "signaling input EOS");
                        if (WORK_AROUND_BUGS) {
                            // Might drop a frame, but at least we won't crash mediaserver.
                            try { Thread.sleep(500); } catch (InterruptedException ie) {}
                            outputDone = true;
                        } else {
                            encoder.signalEndOfInputStream();
                        }
                        inputDone = true;
                    } else {
                        generateSurfaceFrame(generateIndex);
                        inputSurface.setPresentationTime(computePresentationTime(generateIndex) * 1000);
                        Log.d(TAG, "inputSurface swapBuffers");
                        inputSurface.swapBuffers();
                    }
                    generateIndex++;
                }
    
                // Check for output from the encoder.  If there's no output yet, we either need to
                // provide more input, or we need to wait for the encoder to work its magic.  We
                // can't actually tell which is the case, so if we can't get an output buffer right
                // away we loop around and see if it wants more input.
                //
                // If we do find output, drain it all before supplying more input.
                while (true) {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output from encoder available");
                        break;      // out of while
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        encoderOutputBuffers = encoder.getOutputBuffers();
                        Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // not expected for an encoder
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "encoder output format changed: " + newFormat);
                    } else if (encoderStatus < 0) {
                        Log.e(TAG,"unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
    					} else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            Log.e(TAG,"encoderOutputBuffer " + encoderStatus + " was null");
                       }
    
                        // Codec config flag must be set iff this is the first chunk of output.  This
                        // may not hold for all codecs, but it appears to be the case for video/avc.
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        		Log.e(TAG,  "BUFFER_FLAG_CODEC_CONFIG must be set in first chunk");
                        }
                        if (  outputCount == 0) {
                     		Log.e(TAG,  "OutputCount should not be zero in first chunk");
                        }
    
                        if (info.size != 0) {
                            // Adjust the ByteBuffer values to match BufferInfo.
                           encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
    
                            output.addChunk(encodedData, info.flags, info.presentationTimeUs);
                            outputCount++;
                        }
    
                        encoder.releaseOutputBuffer(encoderStatus, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true;
                            break;      // out of while
                        }
                    }
                }
           }
               // One chunk per frame, plus one for the config data.
           if ( outputCount == ( NUM_FRAMES + 1) ) {
        	   		Log.d(TAG, "FrameCount: " + outputCount);
           }
       }
    
    
    /*******************************************************************
    * Checks the frame for correctness, using GL to check RGB values.
    * @param frameIndex  - the index to which frame to check for
    * @return true if the frame looks good
    ********************************************************************/
    private boolean checkSurfaceFrame(int frameIndex) {
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(4);
        boolean frameFailed = false;
        for (int i = 0; i < 8; i++) {
            // Note the coordinates are inverted on the Y-axis in GL.
            int x, y;
            if (i < 4) {
                x = i * (mEncodingWidth / 4) + (mEncodingWidth / 8);
                y = (mEncodingHeight * 3) / 4;
            } else {
                x = (7 - i) * (mEncodingWidth / 4) + (mEncodingWidth / 8);
                y = mEncodingHeight / 4;
            }
            GLES20.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelBuf);
            int r = pixelBuf.get(0) & 0xff;
            int g = pixelBuf.get(1) & 0xff;
            int b = pixelBuf.get(2) & 0xff;
            //Log.d(TAG, "GOT(" + frameIndex + "/" + i + "): r=" + r + " g=" + g + " b=" + b);
            int expR, expG, expB;
            if (i == frameIndex % 8) {
                // colored rect (green/blue swapped)
                expR = TEST_R1;
                expG = TEST_B1;
                expB = TEST_G1;
            } else {
                // zero background color (green/blue swapped)
                expR = TEST_R0;
                expG = TEST_B0;
                expB = TEST_G0;
            }
            if (!isColorClose(r, expR) ||
                    !isColorClose(g, expG) ||
                    !isColorClose(b, expB)) {
                Log.w(TAG, "Bad frame " + frameIndex + " (rect=" + i + ": rgb=" + r +
                        "," + g + "," + b + " vs. expected " + expR + "," + expG +
                        "," + expB + ")");
                frameFailed = true;
            }
        }
        return !frameFailed;
    }
    
   /***************************************************************************************
     * Generates a frame of data using GL commands.
     * <p>
    * We have an 8-frame animation sequence that wraps around.  It looks like this:
    * <pre>
    *   0 1 2 3
    *   7 6 5 4
    * </pre>
    * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
    * @param frameIndex
     ***************************************************************************************/
   private void generateSurfaceFrame(int frameIndex) {
           frameIndex %= 8;
   
           int startX, startY;
           if (frameIndex < 4) {
               // (0,0) is bottom-left in GL
               startX = frameIndex * (mEncodingWidth / 4);
               startY = mEncodingHeight / 2;
           } else {
               startX = (7 - frameIndex) * (mEncodingWidth / 4);
               startY = 0;
           }
   
           GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
           GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
           GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
           GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
           GLES20.glScissor(startX, startY, mEncodingWidth / 4, mEncodingHeight / 2);
           GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
           GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
       }
    
    /****************************************************************************************
    * The elementary stream coming out of the "video/avc" encoder needs to be fed back into
    * the decoder one chunk at a time.  If we just wrote the data to a file, we would lose
    * the information about chunk boundaries.  This class stores the encoded data in memory,
    * retaining the chunk organization.
    ****************************************************************************************/
   private static class VideoChunks {
       private MediaFormat mMediaFormat;
       private ArrayList<byte[]> mChunks = new ArrayList<byte[]>();
       private ArrayList<Integer> mFlags = new ArrayList<Integer>();
       private ArrayList<Long> mTimes = new ArrayList<Long>();

       /**************************************************************
        * Sets the MediaFormat, for the benefit of a future decoder.
        **************************************************************/
       public void setMediaFormat(MediaFormat format) {
           mMediaFormat = format;
       }

        /**
        * Gets the MediaFormat that was used by the encoder.
        */
       public MediaFormat getMediaFormat() {
           return mMediaFormat;
       }

       /************************************************************
        * Adds a new chunk.  Advances buf.position to buf.limit.
        ************************************************************/
       public void addChunk(ByteBuffer buf, int flags, long time) {
           byte[] data = new byte[buf.remaining()];
           buf.get(data);
           mChunks.add(data);
           mFlags.add(flags);
           mTimes.add(time);
       }

       /***********************************************************
        * Returns the number of chunks currently held.
        ***********************************************************/
       public int getNumChunks() {
           return mChunks.size();
       }

       /***********************************************************
       * Copies the data from chunk N into "dest".
        * Advances dest.position.
       ***********************************************************/
       public void getChunkData(int chunk, ByteBuffer dest) {
           byte[] data = mChunks.get(chunk);
           dest.put(data);
       }

       /************************************************************
        * Returns the flags associated with chunk N.
        * @param chunk
        ***********************************************************/
       public int getChunkFlags(int chunk) {
           return mFlags.get(chunk);
       }

       /***************************************************************
        * Returns the timestamp associated with chunk N.
        * @param chunk
        * @return long
        * @see int#getChunkFlags(int chunk)
        ***************************************************************/
       public long getChunkTime(int chunk) {
           return mTimes.get(chunk);
       }

       /******************************************************************************
        * Writes the chunks to a file as a contiguous stream.  Useful for debugging.
        * @param file - the file to save the video to
        *****************************************************************************/
       public void saveToFile(File file) {
           Log.d(TAG, "saving chunk data to file " + file);
           FileOutputStream fos = null;
           BufferedOutputStream bos = null;
           try {
               fos = new FileOutputStream(file);
               bos = new BufferedOutputStream(fos);
               fos = null;     // closing bos will also close fos
               int numChunks = getNumChunks();
               for (int i = 0; i < numChunks; i++) {
                   byte[] chunk = mChunks.get(i);
                   bos.write(chunk);
               }
           } catch (IOException ioe) {
               throw new RuntimeException(ioe);
           } finally {
               try {
                   if (bos != null) {
                       bos.close();
                   }
                   if (fos != null) {
                       fos.close();
                   }
               } catch (IOException ioe) {
                   throw new RuntimeException(ioe);
               }
           }
       }

        /*********************************************
         * Edits a stream of video data.
         *  @param decoder
         * @param outputSurface
         * @param inputSurface
         * @param encoder
         * @param outputData
         ******************************************/
        public void editVideoData(MediaCodec decoder,
                                  OutputSurface outputSurface,
                                  InputSurface inputSurface,
                                  MediaCodec encoder,
                                  VideoChunks outputData) {
            final int TIMEOUT_USEC = 10000;
            ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            BufferInfo info = new BufferInfo();
            int inputChunk = 0;
            int outputCount = 0;
            boolean outputDone = false;
            boolean inputDone = false;
            boolean decoderDone = false;
            while (!outputDone) {
                Log.d(TAG, "edit loop");
                // Feed more data to the decoder.
                if (!inputDone) {
                    int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        if (inputChunk == getNumChunks()) {
                            // End of stream -- send empty frame with EOS flag set.
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                            Log.d(TAG, "sent input EOS (with zero-length frame)");
                        } else {
                            // Copy a chunk of input to the decoder.  The first chunk should have
                            // the BUFFER_FLAG_CODEC_CONFIG flag set.
                            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                            inputBuf.clear();
                            getChunkData(inputChunk, inputBuf);
                            int flags = getChunkFlags(inputChunk);
                            long time = getChunkTime(inputChunk);
                            decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                    time, flags);
                                Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                        inputBuf.position() + " flags=" + flags);
                            }
                            inputChunk++;
                        }
                    } else {
                        Log.d(TAG, "input buffer not available");
                    }
                }
                // Assume output is available.  Loop until both assumptions are false.
                boolean decoderOutputAvailable = !decoderDone;
                boolean encoderOutputAvailable = true;
                while (decoderOutputAvailable || encoderOutputAvailable) {
                    // Start by draining any pending output from the encoder.  It's important to
                    // do this before we try to stuff any more data in.
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output from encoder available");
                        encoderOutputAvailable = false;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        encoderOutputBuffers = encoder.getOutputBuffers();
                        Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "encoder output format changed: " + newFormat);
                    } else if (encoderStatus < 0) {
                        Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            Log.e(TAG,"encoderOutputBuffer " + encoderStatus + " was null");
                        }
                        // Write the data to the output "file".
                        if (info.size != 0) {
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            outputData.addChunk(encodedData, info.flags, info.presentationTimeUs);
                            outputCount++;
                            Log.d(TAG, "encoder output " + info.size + " bytes");
                        }
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Continue attempts to drain output.
                        continue;
                    }
                    // Encoder is drained, check to see if we've got a new frame of output from
                    // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                    // but we still get information through BufferInfo.)
                    if (!decoderDone) {
                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                            Log.d(TAG, "no output from decoder available");
                            decoderOutputAvailable = false;
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            //decoderOutputBuffers = decoder.getOutputBuffers();
                            Log.d(TAG, "decoder output buffers changed (we don't care)");
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // expected before first buffer of data
                            MediaFormat newFormat = decoder.getOutputFormat();
                            Log.d(TAG, "decoder output format changed: " + newFormat);
                        } else if (decoderStatus < 0) {
                            Log.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                        } else { // decoderStatus >= 0
                             Log.d(TAG, "surface decoder given buffer "
                                    + decoderStatus + " (size=" + info.size + ")");
                            // The ByteBuffers are null references, but we still get a nonzero
                            // size for the decoded data.
                            boolean doRender = (info.size != 0);
                            // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                            // to SurfaceTexture to convert to a texture.  The API doesn't
                            // guarantee that the texture will be available before the call
                            // returns, so we need to wait for the onFrameAvailable callback to
                            // fire.  If we don't wait, we risk rendering from the previous frame.
                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                            if (doRender) {
                                // This waits for the image and renders it after it arrives.
                                Log.d(TAG, "awaiting frame");
                                outputSurface.awaitNewImage();
                                outputSurface.drawImage();
                                // Send it to the encoder.
                                inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                Log.d(TAG, "swapBuffers");
                                inputSurface.swapBuffers();
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                // forward decoder EOS to encoder
                                Log.d(TAG, "signaling input EOS");
                                if (WORK_AROUND_BUGS) {
                                    // Bail early, possibly dropping a frame.
                                    return;
                                } else {
                                    encoder.signalEndOfInputStream();
                                }
                            }
                        }
                    }
                }
            if (inputChunk != outputCount) {
                throw new RuntimeException("frame lost: " + inputChunk + " in, " +
                        outputCount + " out");
            }
        }
    }
}
