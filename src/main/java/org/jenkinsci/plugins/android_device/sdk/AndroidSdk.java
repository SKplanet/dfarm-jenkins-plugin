package org.jenkinsci.plugins.android_device.sdk;

import java.io.IOException;
import java.io.Serializable;

public class AndroidSdk implements Serializable {
    private static final long serialVersionUID = 1L;

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
}