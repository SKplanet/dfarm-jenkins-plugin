package org.jenkinsci.plugins.android_device;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by skyisle on 08/25/2014.
 */
public class AndroidRemote extends BuildWrapper {

    /**
     * Helper method for writing to the build log in a consistent manner.
     */
    public synchronized static void log(final PrintStream logger, final String message) {
        log(logger, message, false);
    }

    /**
     * Helper method for writing to the build log in a consistent manner.
     */
    public synchronized static void log(final PrintStream logger, final String message, final Throwable t) {
        log(logger, message, false);
        StringWriter s = new StringWriter();
        t.printStackTrace(new PrintWriter(s));
        log(logger, s.toString(), false);
    }

    /**
     * Helper method for writing to the build log in a consistent manner.
     */
    synchronized static void log(final PrintStream logger, String message, boolean indent) {
        if (indent) {
            message = '\t' + message.replace("\n", "\n\t");
        } else if (message.length() > 0) {
            logger.print("[android] ");
        }
        logger.println(message);
    }


    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        // connect api server

        try {
            connectApiServer();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // wait for response

        // find sdk path
        AndroidSdk sdk = new AndroidSdk("/devel/android-sdk", "/devel/android-sdk");
        // connect device with adb

        String ip = "10.203.202.178";
        int port = 5555;
        final AndroidDeviceContext device = new AndroidDeviceContext(build, launcher, listener, sdk, ip, port);

        device.connect(15000);
        // check availability

        // unlock screen
        device.unlockScreen();

        // Start dumping logcat to temporary file
        final File artifactsDir = build.getArtifactsDir();
        final FilePath logcatFile = build.getWorkspace().createTextTempFile("logcat_", ".log", "", false);
        final OutputStream logcatStream = logcatFile.write();

        device.startLogcatProc(logcatStream);

        return new BuildWrapper.Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("ANDROID_IP", device.ip());
                env.put("ANDROID_PORT", Integer.toString(device.port()));
            }
        };
    }

    public void connectApiServer() throws URISyntaxException {
        final Socket socket = IO.socket("http://10.202.35.214:9001");
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            public void call(Object... args) {
                socket.emit("foo", "hi");
                socket.disconnect();
            }

        }).on("event", new Emitter.Listener() {
            public void call(Object... args) {
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            public void call(Object... args) {
            }

        });
        socket.connect();
    }
}
