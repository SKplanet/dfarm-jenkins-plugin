package org.jenkinsci.plugins.android_device.api;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.android_device.FailedToConnectApiServerException;
import org.jenkinsci.plugins.android_device.Messages;

import java.io.PrintStream;
import java.net.URISyntaxException;

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

    public void connectApiServer(final PrintStream logger, final StringBuffer buffer, String deviceApiUrl, final String tag, final String jobId) throws FailedToConnectApiServerException {
        try {
            apiSocket = IO.socket(deviceApiUrl);
            apiSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                public void call(Object... args) {
                    JSONObject object = new JSONObject();
                    object.put(KEY_TAG, tag);
                    object.put(KEY_ID, jobId);
                    apiSocket.emit(KEY_JEN_DEVICE, object.toString());
                }

            }).on(KEY_SVC_DEVICE, new Emitter.Listener() {
                public void call(Object... args) {
                    buffer.append(args[0]);
                }
            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                public void call(Object... args) {
                    log(logger, Messages.API_SERVER_DISCONNECTED());
                }
            });
            apiSocket.connect();
        } catch (URISyntaxException e) {
            throw new FailedToConnectApiServerException(e);
        }
    }

    public void disconnect() {
        if (apiSocket != null) {
            apiSocket.emit(KEY_JEN_OUT, "bye");
            apiSocket.disconnect();
        }
    }
}
