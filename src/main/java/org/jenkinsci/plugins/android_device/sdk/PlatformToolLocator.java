package org.jenkinsci.plugins.android_device.sdk;

import org.jenkinsci.plugins.android_device.SdkInstallationException;

import java.io.File;

class PlatformToolLocator implements ToolLocator {
    private static final String BUILD_TOOLS_PATH = File.separator + "build-tools" + File.separator;

    public String findInSdk(AndroidSdk androidSdk, Tool tool) throws SdkInstallationException {
        if (tool == Tool.AAPT) {
            File buildToolsDir = new File(androidSdk.getSdkRoot() + BUILD_TOOLS_PATH);
            if (buildToolsDir.exists()) {
                String[] subDirs = buildToolsDir.list();
                // TODO: Maybe we should be using the newest toolset available?
                return getFirstInstalledBuildToolsDir(subDirs);
            }
        }
        return File.separator + "platform-tools" + File.separator;

    }

    private String getFirstInstalledBuildToolsDir(String[] buildToolsDirs) throws SdkInstallationException {
        if (buildToolsDirs.length == 0) {
            throw new SdkInstallationException("Please install at least one set of build-tools.");
        }
        return BUILD_TOOLS_PATH + buildToolsDirs[0] + File.separator;
    }
}