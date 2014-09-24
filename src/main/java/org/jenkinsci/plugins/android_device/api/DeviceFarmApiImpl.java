package org.jenkinsci.plugins.android_device.api;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.android_device.FailedToConnectApiServerException;
import org.jenkinsci.plugins.android_device.Messages;
import org.jenkinsci.plugins.android_device.RemoteDevice;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.concurrent.TimeoutException;

import static org.jenkinsci.plugins.android_device.AndroidRemote.log;

/**
 * Created by skyisle on 08/27/2014.
 */
public class DeviceFarmApiImpl implements DeviceFarmApi {

    public static final String CONNECTION_TIMEOUT = "connection_timeout";
    public static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private Socket apiSocket;
    private StringBuffer buffer;

    public void connectApiServer(final PrintStream logger, String deviceApiUrl, final String tag, final String jobId) throws FailedToConnectApiServerException {
        connectApiServer(logger, deviceApiUrl, tag, jobId, DEFAULT_CONNECT_TIMEOUT);
    }

    public void connectApiServer(final PrintStream logger, String deviceApiUrl, final String tag, final String jobId, long connect_timeout) throws FailedToConnectApiServerException {
        try {
            buffer = new StringBuffer();
            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = false;
            options.timeout = connect_timeout;

            apiSocket = IO.socket(deviceApiUrl, options);
            apiSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                public void call(Object... args) {
                    log(logger, Messages.API_SERVER_CONNECTED());
                    String s = buildJenDeviceValue(tag, jobId);
                    apiSocket.emit(KEY_JEN_DEVICE, s);
                }

            }).on(KEY_SVC_DEVICE, new Emitter.Listener() {
                public void call(Object... args) {
                    buffer.append(args[0]);
                }
            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                public void call(Object... args) {
                    log(logger, Messages.API_SERVER_DISCONNECTED());
                    apiSocket.disconnect();
                }
            }).on(Socket.EVENT_CONNECT_TIMEOUT, new Emitter.Listener() {
                public void call(Object... objects) {
                    buffer.append(CONNECTION_TIMEOUT);
                }
            });
            apiSocket.connect();
        } catch (URISyntaxException e) {
            throw new FailedToConnectApiServerException(e);
        }
    }

    private String buildJenDeviceValue(String tag, String jobId) {
        JSONObject object = new JSONObject();
        object.put(KEY_TAG, tag);
        try {
            object.put(KEY_ID, URLEncoder.encode(jobId, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    public RemoteDevice waitApiResponse(PrintStream logger, int timeout_in_ms, int check_interval_in_ms) throws MalformedResponseException, TimeoutException, FailedToConnectApiServerException {
        long start = System.currentTimeMillis();
        while (buffer.length() == 0 &&
                System.currentTimeMillis() < start + timeout_in_ms) {

            log(logger, Messages.WAITING_FOR_DEVICE());
            try {
                Thread.sleep(check_interval_in_ms);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (buffer.length() == 0) {
            throw new TimeoutException();
        }

        if (CONNECTION_TIMEOUT.endsWith(buffer.toString())) {
            throw new FailedToConnectApiServerException("Connection timeout");
        }

        try {
            JSONObject jsonObject = JSONObject.fromObject(buffer.toString());
            log(logger, Messages.DEVICE_READY_RESPONSE(jsonObject.optString(KEY_TAG)));
            String ip = jsonObject.getString(KEY_IP);
            int port = jsonObject.getInt(KEY_PORT);
            String url = jsonObject.optString(KEY_URL);
            return new RemoteDevice(ip, port, url);
        } catch (JSONException e) {
            log(logger, Messages.FAILED_TO_PARSE_DEVICE_FARM_RESPONSE());
            throw new MalformedResponseException(e);
        }
    }

    public void disconnect() {
        if (apiSocket != null) {
            apiSocket.emit(KEY_JEN_OUT, "bye");
            apiSocket.disconnect();
        }
    }
}
