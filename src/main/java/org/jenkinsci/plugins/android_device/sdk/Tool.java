package org.jenkinsci.plugins.android_device.sdk;

import org.jenkinsci.plugins.android_device.SdkInstallationException;

public enum Tool {
    AAPT("aapt", ".exe", new PlatformToolLocator()),
    ADB("adb", ".exe", new PlatformToolLocator()),
    ANDROID("android", ".bat");

    public static Tool[] REQUIRED = new Tool[] {
        AAPT, ADB, ANDROID
    };

    public final String executable;
    public final String windowsExtension;
    public final ToolLocator toolLocator;
    Tool(String executable, String windowsExtension) {
        this(executable, windowsExtension, new DefaultToolLocator());
    }

    Tool(String executable, String windowsExtension, ToolLocator toolLocator) {
        this.executable = executable;
        this.windowsExtension = windowsExtension;
        this.toolLocator = toolLocator;
    }

    public String getExecutable(boolean isUnix) {
        if (isUnix) {
            return executable;
        }
        return executable + windowsExtension;
    }

    public String findInSdk(AndroidSdk androidSdk) throws SdkInstallationException {
        return toolLocator.findInSdk(androidSdk, this);
    }

    public static String[] getAllExecutableVariants() {
        return getAllExecutableVariants(values());
    }

    public static String[] getAllExecutableVariants(final Tool[] tools) {
        String[] executables = new String[tools.length * 2];
        for (int i = 0, n = tools.length; i < n; i++) {
            executables[i*2] = tools[i].getExecutable(true);
            executables[i*2+1] = tools[i].getExecutable(false);
        }

        return executables;
    }

    @Override
    public String toString() {
        return executable;
    }
}