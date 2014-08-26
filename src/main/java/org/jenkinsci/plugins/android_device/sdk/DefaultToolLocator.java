package org.jenkinsci.plugins.android_device.sdk;

import java.io.File;

class DefaultToolLocator implements ToolLocator {
    public String findInSdk(AndroidSdk androidSdk, Tool tool) {
        return File.separator + "tools" + File.separator;
    }
}