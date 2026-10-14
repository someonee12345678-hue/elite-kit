package com.elitekit.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HarnessSmokeTest {

    @Test
    public void testHarnessIsFunctional() {
        String appName = "ELITE KIT";
        assertNotNull(appName);
        assertEquals("ELITE KIT", appName);
    }
}
