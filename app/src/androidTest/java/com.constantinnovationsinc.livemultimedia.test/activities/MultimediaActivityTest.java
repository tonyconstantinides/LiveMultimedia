package com.constantinnovationsinc.livemultimedia.test.activities;


import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import com.constantinnovationsinc.livemultimedia.activities.LiveMultimediaActivity;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MultimediaActivityTest
        extends  ActivityInstrumentationTestCase2 {

    public MultimediaActivityTest() {
        super(LiveMultimediaActivity.class);
    }

    @Test
    public void testOnCreate() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void testOnDestroy() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void testOnLowMemory() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void testOnPause() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void testOnResume() throws Exception {
        assertNotNull(null);
    }
}