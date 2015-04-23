package com.constantinnovationsinc.livemultimedia.test.activities;

import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import com.constantinnovationsinc.livemultimedia.activities.LiveMultimediaActivity;
import java.lang.Override;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.runner.AndroidJUnitRunner;
import com.constantinnovationsinc.livemultimedia.app.MultimediaApp;
import java.lang.Override;
import org.junit.After;
import org.junit.Before;
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