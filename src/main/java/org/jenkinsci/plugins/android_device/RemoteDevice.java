package org.jenkinsci.plugins.android_device;

/**
 * Created by skyisle on 08/27/2014.
 */
public class RemoteDevice {
    public String ip;
    public int port;
    public String url;

    public RemoteDevice(String ip, int port, String url) {
        this.ip = ip;
        this.port = port;
        this.url = url;
    }
}
