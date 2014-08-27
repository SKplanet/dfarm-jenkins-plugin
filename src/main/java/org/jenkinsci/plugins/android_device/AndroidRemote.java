package org.jenkinsci.plugins.android_device;

import com.google.common.base.Strings;
import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.android_device.api.DeviceFarmApi;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.util.Utils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.*;
import java.util.Map;

import static org.jenkinsci.plugins.android_device.api.DeviceFarmApi.*;

/**
 * Created by skyisle on 08/25/2014.
 */
public class AndroidRemote extends BuildWrapper {

    public static final int API_TIMEOUT_IN_MILLIS = 7200;
    public static final int DEVICE_WAIT_TIMEOUT_IN_MILLIS = 5 * 60 * 1000;
    public static final int DEVICE_CONNECT_TIMEOUT_IN_MILLIS = 15000;
    private static final int KILL_PROCESS_TIMEOUT_MS = 5000;

    @Exported
    public String deviceApiUrl;
    @Exported
    public String tag;

    @DataBoundConstructor
    public AndroidRemote(String deviceApiUrl, String tag) {
        this.deviceApiUrl = deviceApiUrl;
        this.tag = tag;
    }

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
        final PrintStream logger = listener.getLogger();

        final DeviceFarmApi api = new DeviceFarmApi();
        StringBuffer responseBuffer = new StringBuffer();
        try {
            log(logger, Messages.TRYING_TO_CONNECT_API_SERVER(deviceApiUrl));
            api.connectApiServer(logger, responseBuffer, deviceApiUrl, tag, build.getFullDisplayName());
        } catch (FailedToConnectApiServerException e) {
            log(logger, Messages.FAILD_TO_CONNECT_API_SERVER());
            build.setResult(Result.NOT_BUILT);
            return null;
        }

        final RemoteDevice reserved = waitApiResponse(logger, responseBuffer, DEVICE_WAIT_TIMEOUT_IN_MILLIS);
        if (reserved == null) {
            log(logger, Messages.DEVICE_WAIT_TIMEOUT());
            build.setResult(Result.NOT_BUILT);
            cleanUp(null, api, null, null, null, null);
            return null;
        }

        // find sdk path
        AndroidSdk sdk = new AndroidSdk("/devel/android-sdk", "/devel/android-sdk");
        // connect device with adb

        final AndroidDeviceContext device = new AndroidDeviceContext(build, launcher, listener, sdk, reserved.ip, reserved.port);
        device.connect(DEVICE_CONNECT_TIMEOUT_IN_MILLIS);

        // check availability
        device.devices();

        // unlock screen
        device.powerOn();
        device.unlockScreen();

        // Start dumping logcat to temporary file
        final File artifactsDir = build.getArtifactsDir();
        final FilePath logcatFile = build.getWorkspace().createTextTempFile("logcat_", ".log", "", false);
        final OutputStream logcatStream = logcatFile.write();
        final Proc logWriter = device.startLogcatProc(logcatStream);

        return new BuildWrapper.Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("ANDROID_IP", device.ip());
                env.put("ANDROID_PORT", Integer.toString(device.port()));
            }

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                cleanUp(device, api, logWriter, logcatFile, logcatStream, artifactsDir);

                return true;
            }
        };
    }

    private void cleanUp(AndroidDeviceContext device, DeviceFarmApi api, Proc logcatProcess, FilePath logcatFile, OutputStream logcatStream, File artifactsDir) throws IOException, InterruptedException {
        if (device != null) {
            device.disconnect();
        }

        saveLogcat(logcatProcess, logcatFile, logcatStream, artifactsDir);
        api.disconnect();
    }

    private void saveLogcat(Proc logcatProcess, FilePath logcatFile, OutputStream logcatStream, File artifactsDir) throws IOException, InterruptedException {
        if (logcatProcess != null) {
            if (logcatProcess.isAlive()) {
                // This should have stopped when the emulator was,
                // but if not attempt to kill the process manually.
                // First, give it a final chance to finish cleanly.
                Thread.sleep(3 * 1000);
                if (logcatProcess.isAlive()) {
                    Utils.killProcess(logcatProcess, KILL_PROCESS_TIMEOUT_MS);
                }
            }
            try {
                logcatStream.close();
            } catch (Exception ignore) {
            }

            // Archive the logs
            if (logcatFile.length() != 0) {
                logcatFile.copyTo(new FilePath(artifactsDir).child("logcat.txt"));
            }
            logcatFile.delete();
        }
    }

    private RemoteDevice waitApiResponse(PrintStream logger, StringBuffer response, int timeout_in_ms) {
        long start = System.currentTimeMillis();
        while (response.length() == 0 &&
                System.currentTimeMillis() < start + timeout_in_ms) {

            log(logger, Messages.WAITING_FOR_DEVICE());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (response.length() == 0) {
            return null;
        }

        //{port:6667, tag:'SHV-E330S,4.2.1'}
        JSONObject jsonObject = JSONObject.fromObject(response.toString());
        log(logger, Messages.DEVICE_READY_RESPONSE(jsonObject.optString(KEY_TAG)));
        return new RemoteDevice(jsonObject.getString(KEY_IP), jsonObject.getInt(KEY_PORT));
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The Android SDK home directory.  Can include variables, e.g. <tt>${ANDROID_HOME}</tt>.
         * <p>If <code>null</code>, we will just assume the required commands are on the PATH.</p>
         */
        public String androidHome;
        private String deviceApiUrl;

        public DescriptorImpl() {
            super(AndroidRemote.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.JOB_DESCRIPTION();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            deviceApiUrl = json.optString("deviceApiUrl");
            androidHome = json.optString("androidSdkHome");
            save();
            return true;
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String deviceApiUrl = null;
            String tag = null;

            deviceApiUrl = formData.optString("deviceApiUrl");
            if (Strings.isNullOrEmpty(deviceApiUrl)) {
                deviceApiUrl = this.deviceApiUrl;
            }
            tag = formData.optString("tag");

            return new AndroidRemote(deviceApiUrl, tag);
        }

        @Override
        public String getHelpFile() {
            return Functions.getResourcePath() + "/plugin/android-device/help-buildConfig.html";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
//
//        /** Used in config.jelly: Lists the OS versions available. */
//        public AndroidPlatform[] getAndroidVersions() {
//            return AndroidPlatform.ALL;
//        }
//
//        /** Used in config.jelly: Lists the screen densities available. */
//        public ScreenDensity[] getDeviceDensities() {
//            return ScreenDensity.PRESETS;
//        }
    }
}