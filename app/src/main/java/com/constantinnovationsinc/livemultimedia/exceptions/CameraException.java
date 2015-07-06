package com.constantinnovationsinc.livemultimedia.exceptions;
import android.util.Log;

public class CameraException extends Exception{
    private static final String TAG = CameraException.class.getCanonicalName();

    public CameraException() {
        super();
    }
    public CameraException(String detailMessage) {
        super(detailMessage);
    }

    public CameraException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
        Log.e(TAG, detailMessage);
    }
}
