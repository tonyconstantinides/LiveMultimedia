package com.constantinnovationsinc.livemultimedia.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentActivity;

import com.constantinnovationsinc.livemultimedia.R;
import com.constantinnovationsinc.livemultimedia.fragments.Camera2VideoFragment;


public class LiveMultimediaActivity extends FragmentActivity {
    private static final String TAG = LiveMultimediaActivity.class.getName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hwbroadcasting);
        if (savedInstanceState != null) {
            return;
        }
        if (findViewById(R.id.fragment_container) != null) {
            if (savedInstanceState == null) {
                Log.d(TAG, "Creating the fragment contains the video fragment.");
                Fragment fragment = new Camera2VideoFragment();
                fragment.setArguments( getIntent().getExtras() );
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.add(R.id.fragment_container, fragment, "CameraFrag");
                transaction.addToBackStack( "CameraFrag" );
                transaction.commit();
            }
        }
        String test = "Hello";
        switch(test) {
            case "Hello":
              break;
            case "Crap":
             break;
            default:
            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.hwbroadcasting, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
