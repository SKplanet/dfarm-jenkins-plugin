package org.jenkinsci.plugins.android_device;

public final class SdkInstallationException extends AndroidRemoteException {

    public SdkInstallationException(String message) {
        super(message);
    }

    SdkInstallationException(String message, Throwable cause) {
        super(message, cause);
    }

    private static final long serialVersionUID = 1L;

}