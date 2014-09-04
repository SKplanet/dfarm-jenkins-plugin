package org.jenkinsci.plugins.android_device.api;

import org.jenkinsci.plugins.android_device.FailedToConnectApiServerException;
import org.jenkinsci.plugins.android_device.RemoteDevice;

import java.io.PrintStream;
import java.util.concurrent.TimeoutException;

/**
 * Created by skyisle on 08/29/2014.
 */
public interface DeviceFarmApi {
    String KEY_PORT = "port";
    String KEY_TAG = "tag";
    String KEY_JEN_DEVICE = "jen_device";
    String KEY_JEN_OUT = "jen_out";
    String KEY_SVC_DEVICE = "svc_device";
    String KEY_IP = "ip";
    String KEY_ID = "id";

    void connectApiServer(PrintStream logger, String deviceApiUrl, String tag, String jobId) throws FailedToConnectApiServerException;
    void connectApiServer(PrintStream logger, String deviceApiUrl, String tag, String jobId, long connect_timeout) throws FailedToConnectApiServerException;

    RemoteDevice waitApiResponse(PrintStream logger, int timeout_in_ms, int check_interval_in_ms) throws MalformedResponseException, TimeoutException, FailedToConnectApiServerException;

    void disconnect();
}
