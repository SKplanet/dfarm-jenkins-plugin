package org.jenkinsci.plugins.android_device.api;

/**
 * Created by skyisle on 08/28/2014.
 */
public class MalformedResponseException extends Throwable {
    public MalformedResponseException(Exception e) {
        super(e);
    }
}
