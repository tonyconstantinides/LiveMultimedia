package com.constantinnovationsinc.livemultimedia.test.app;

import android.test.ApplicationTestCase;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.runner.AndroidJUnitRunner;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import java.lang.Override;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.constantinnovationsinc.livemultimedia.app.MultimediaApp;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class MultimediaAppTest extends ApplicationTestCase<MultimediaApp> {
    MultimediaApp mApplication = null;
    public MultimediaAppTest() {
        super(MultimediaApp.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        createApplication();
        mApplication = getApplication();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void SaveAudioData() throws Exception {
           assertNotNull(null);
    }

    @Test
    public void PullAudioData() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void Capacity() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void OnCreate() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void OnLowMemory() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void RegisterComponentCallbacks() throws Exception {
        assertNotNull(null);
    }

    @Test
    public void OnTerminate() throws Exception {
        assertNotNull(null);
    }
}