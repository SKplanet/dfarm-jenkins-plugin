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
public class DeviceFarmApi {
    public static final String KEY_PORT = "port";
    public static final String KEY_TAG = "tag";
    public static final String KEY_JEN_DEVICE = "jen_device";
    public static final String KEY_JEN_OUT = "jen_out";
    public static final String KEY_SVC_DEVICE = "svc_device";
    public static final String KEY_IP = "ip";
    public static final String KEY_ID = "id";

    private Socket apiSocket;
    private StringBuffer buffer;

    public void connectApiServer(final PrintStream logger, String deviceApiUrl, final String tag, final String jobId) throws FailedToConnectApiServerException {
        try {
            buffer = new StringBuffer();
            apiSocket = IO.socket(deviceApiUrl);
            apiSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                public void call(Object... args) {
                    JSONObject object = new JSONObject();
                    object.put(KEY_TAG, tag);
                    try {
                        object.put(KEY_ID, URLEncoder.encode(jobId, "utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    apiSocket.emit(KEY_JEN_DEVICE, object.toString());
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
            });
            apiSocket.connect();
        } catch (URISyntaxException e) {
            throw new FailedToConnectApiServerException(e);
        }
    }

    public RemoteDevice waitApiResponse(PrintStream logger, int timeout_in_ms, int check_interval_in_ms) throws MalformedResponseException, TimeoutException {
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

        //{port:6667, tag:'SHV-E330S,4.2.1'}

        try {
            JSONObject jsonObject = JSONObject.fromObject(buffer.toString());
            log(logger, Messages.DEVICE_READY_RESPONSE(jsonObject.optString(KEY_TAG)));
            String ip = jsonObject.getString(KEY_IP);
            int port = jsonObject.getInt(KEY_PORT);
            return new RemoteDevice(ip, port);
        } catch (JSONException e) {
            log(logger, Messages.FAILED_TO_PARSE_DEVICE_FARM_RESPONSE());
            throw new MalformedResponseException(e);
        }
    }

    public void disconnect() {
        if (apiSocket != null) {
            apiSocket.emit(KEY_JEN_OUT, "bye");
            apiSocket.emit(Socket.EVENT_DISCONNECT, "bye");
            apiSocket.disconnect();
        }
    }
}
