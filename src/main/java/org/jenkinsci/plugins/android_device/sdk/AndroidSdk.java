package org.jenkinsci.plugins.android_device.sdk;

import hudson.EnvVars;
import org.jenkinsci.plugins.android_device.util.Utils;

import java.io.IOException;
import java.io.Serializable;

public class AndroidSdk implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ANDROID_SDK_HOME = "ANDROID_SDK_HOME";
    public static final String LD_LIBRARY_PATH = "LD_LIBRARY_PATH";

    private final String sdkRoot;
    private final String sdkHome;

    public AndroidSdk(String root, String home) throws IOException {
        this.sdkRoot = root;
        this.sdkHome = home;
    }

    public boolean hasKnownRoot() {
        return this.sdkRoot != null;
    }

    public String getSdkRoot() {
        return this.sdkRoot;
    }

    public boolean hasKnownHome() {
        return this.sdkHome != null;
    }

    public String getSdkHome() {
        return this.sdkHome;
    }

    public void setupEnvVars(EnvVars buildEnvironment) {
        if (hasKnownHome()) {
            buildEnvironment.put(ANDROID_SDK_HOME, getSdkHome());
        }
        if (Utils.isUnix()) {
            buildEnvironment.put(LD_LIBRARY_PATH, String.format("%s/tools/lib", getSdkRoot()));
        }
    }

}