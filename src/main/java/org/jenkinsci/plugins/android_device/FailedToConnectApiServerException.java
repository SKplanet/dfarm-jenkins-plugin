package org.jenkinsci.plugins.android_device;

/**
 * Created by skyisle on 08/27/2014.
 */
public class FailedToConnectApiServerException extends Throwable {
    public FailedToConnectApiServerException(Exception e) {
        super(e);
    }

    public FailedToConnectApiServerException(String msg) {
        super(msg);
    }
}
