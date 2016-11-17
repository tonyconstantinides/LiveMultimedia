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

/********************************************************************
 * LiveMMultimediaActivity
 * Us this Activity class to load the CameraFragment class
 * that encapsulates the custom view that does all the heavy lifting
 ********************************************************************/
public class LiveMultimediaActivity extends FragmentActivity {
    private static final String TAG = LiveMultimediaActivity.class.getCanonicalName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hwbroadcasting);
        if (savedInstanceState != null) {
            return;
        }
        if (findViewById(R.id.fragment_container) != null) {
                Log.d(TAG, "Creating the fragment contains the video fragment.");
                Fragment fragment = new Camera2VideoFragment();
                fragment.setArguments( getIntent().getExtras() );
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.add(R.id.fragment_container, fragment, "CameraFrag");
                transaction.addToBackStack( "CameraFrag" );
                transaction.commit();
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
        return  id == R.id.action_settings || super.onOptionsItemSelected(item);
    }
}
