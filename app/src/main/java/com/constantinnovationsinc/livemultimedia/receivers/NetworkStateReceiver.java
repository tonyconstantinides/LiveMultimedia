package com.constantinnovationsinc.livemultimedia.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;


public class NetworkStateReceiver extends BroadcastReceiver {
    public NetworkStateReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("app","Network connectivity change");
        if(intent.getExtras()!=null) {
            final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
            if(ni != null && ni.isConnectedOrConnecting()) {
                Log.i("app", "Network " + ni.getTypeName() + " connected");
            }
            if (ni != null && ni.isAvailable()) {
                Log.i("app", "Network " + ni.getTypeName() + " is avaialable");
            }
            NetworkInfo.DetailedState state = ni.getDetailedState();
            if (state.compareTo(NetworkInfo.DetailedState.BLOCKED) == 0) {
                Log.d("app", "Network is bloacked");
            }
            if(intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,Boolean.FALSE)) {
                Log.d("app", "There's no network connectivity");
            }
         }
    }
}
