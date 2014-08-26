package org.jenkinsci.plugins.android_device.sdk;

import org.jenkinsci.plugins.android_device.SdkInstallationException;

interface ToolLocator {
    String findInSdk(AndroidSdk androidSdk, Tool tool)throws SdkInstallationException;
}