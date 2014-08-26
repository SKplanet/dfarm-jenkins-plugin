package org.jenkinsci.plugins.android_device;

abstract class AndroidRemoteException extends Exception {

    protected AndroidRemoteException(String message) {
        super(message);
    }

    protected AndroidRemoteException(String message, Throwable cause) {
        super(message, cause);
    }

    private static final long serialVersionUID = 1L;

}