package com.constantinnovationsinc.livemultimedia.servers;

import android.os.Environment;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import com.constantinnovationsinc.livemultimedia.servers.NanoHTTPD.Response.Status;
import android.util.Log;

public class VideoServer extends NanoHTTPD {

    public VideoServer(String hostname, int port) {
        super(hostname, 8080);
    }

    @Override
    public Response serve(IHTTPSession session) {
       FileInputStream fis = null;
        try {
            fis = new FileInputStream(Environment.getExternalStorageDirectory()
                    + "/Movies/EncodedAV-1280x720.mp4");
        } catch (FileNotFoundException e) {
            Log.e("VideoServer: ", e.toString() );
        }
        return new NanoHTTPD.Response(Status.OK, "video/mp4", fis);
    }
}