package org.jenkinsci.plugins.android_device.sdk;

import hudson.EnvVars;
import org.jenkinsci.plugins.android_device.util.Utils;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class AndroidSdkTest {

    public static final String DEVEL_ANDROID_SDK = "/devel/android-sdk";

    @Test
    public void testSetupEnvVars() throws Exception {
        AndroidSdk sdk = new AndroidSdk(DEVEL_ANDROID_SDK, DEVEL_ANDROID_SDK);
        EnvVars envVars = new EnvVars();
        sdk.setupEnvVars(envVars);

        assertThat(envVars.get(AndroidSdk.ANDROID_SDK_HOME), is(equalTo(DEVEL_ANDROID_SDK)));
        if (Utils.isUnix()) {
            assertThat(envVars.get(AndroidSdk.LD_LIBRARY_PATH), is(equalTo(DEVEL_ANDROID_SDK + "/tools/lib")));
        }
    }

    @Test
    public void testSdkRootPath() throws Exception {
        AndroidSdk sdk = new AndroidSdk(DEVEL_ANDROID_SDK, null);
        assertThat(sdk.hasKnownRoot(), is(Boolean.TRUE));
        assertThat(sdk.getSdkRoot(), is(equalTo(DEVEL_ANDROID_SDK)));
    }


    @Test
    public void testSdkHomePath() throws Exception {
        AndroidSdk sdk = new AndroidSdk(null, DEVEL_ANDROID_SDK);
        assertThat(sdk.hasKnownHome(), is(Boolean.TRUE));
        assertThat(sdk.getSdkHome(), is(equalTo(DEVEL_ANDROID_SDK)));
    }
}