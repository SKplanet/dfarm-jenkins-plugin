package org.jenkinsci.plugins.android_device;

import com.google.common.base.Strings;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.android_device.api.DeviceFarmApi;
import org.jenkinsci.plugins.android_device.api.DeviceFarmApiImpl;
import org.jenkinsci.plugins.android_device.api.MalformedResponseException;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.sdk.SdkUtils;
import org.jenkinsci.plugins.android_device.util.ReplaceFilterOutputStream;
import org.jenkinsci.plugins.android_device.util.Utils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Created by skyisle on 08/25/2014.
 */
public class AndroidRemote extends BuildWrapper {

    public static final int DEVICE_WAIT_TIMEOUT_IN_MILLIS = 5 * 60 * 1000;
    public static final int DEVICE_CONNECT_TIMEOUT_IN_MILLIS = 15000;
    private static final int KILL_PROCESS_TIMEOUT_MS = 5000;
    public static final int DEVICE_READY_CHECK_INTERVAL_IN_MS = 5000;
    public static final String ARTIFACT_LOGCAT_TXT = "logcat.txt";

    @Exported
    public String deviceApiUrl;
    @Exported
    public String tag;
    private DescriptorImpl descriptor;

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

        final DeviceFarmApi api = new DeviceFarmApiImpl();
        long start = System.currentTimeMillis();

        try {
            log(logger, Messages.TRYING_TO_CONNECT_API_SERVER(deviceApiUrl));
            api.connectApiServer(logger, deviceApiUrl, tag, build.getFullDisplayName());

            final RemoteDevice reserved = api.waitApiResponse(logger,
                    DEVICE_WAIT_TIMEOUT_IN_MILLIS, DEVICE_READY_CHECK_INTERVAL_IN_MS);
            log(logger, Messages.DEVICE_IS_READY((System.currentTimeMillis() - start) / 1000));

            if (descriptor == null) {
                descriptor = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
            }

            // Substitute environment and build variables into config
            String androidHome = discoverAndroidSdkHome(build, launcher, listener);
            log(logger, Messages.USING_SDK(androidHome));

            AndroidSdk sdk = new AndroidSdk(androidHome, androidHome);
            final AndroidDeviceContext device = new AndroidDeviceContext(build, launcher, listener, sdk, reserved.ip, reserved.port);
            // connect device with adb
            device.connect(DEVICE_CONNECT_TIMEOUT_IN_MILLIS);

            device.waitDeviceReady(logger, DEVICE_CONNECT_TIMEOUT_IN_MILLIS, 1000);
            // check availability
            device.devices();

            // unlock screen
            device.powerOn();
            device.unlockScreen();

            // Start dumping logcat to temporary file
            final LogcatCollector logcatCollector = new LogcatCollector(build, device);
            logcatCollector.start();

            return new BuildWrapper.Environment() {
                @Override
                public void buildEnvVars(Map<String, String> env) {
                    env.put("ANDROID_IP", device.ip());
                    env.put("ANDROID_PORT", Integer.toString(device.port()));
                }

                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener)
                        throws IOException, InterruptedException {
                    cleanUp(build, device, api, logcatCollector);

                    return true;
                }
            };

        } catch (FailedToConnectApiServerException e) {
            log(logger, Messages.FAILED_TO_CONNECT_API_SERVER());
        } catch (MalformedResponseException e) {
            log(logger, Messages.FAILED_TO_PARSE_DEVICE_FARM_RESPONSE());
        } catch (TimeoutException e) {
            log(logger, Messages.DEVICE_WAIT_TIMEOUT((System.currentTimeMillis() - start) / 1000));
        }

        build.setResult(Result.NOT_BUILT);
        cleanUp(null, null, api, null);
        return null;
    }

    private String discoverAndroidSdkHome(AbstractBuild build, Launcher launcher, BuildListener listener) {
        final EnvVars envVars = Utils.getEnvironment(build, listener);
        final Map<String, String> buildVars = build.getBuildVariables();

        // SDK location
        Node node = Computer.currentComputer().getNode();
        String androidHome = Utils.expandVariables(envVars, buildVars, descriptor.androidHome);
        androidHome = SdkUtils.discoverAndroidHome(launcher, node, envVars, androidHome);
        return androidHome;
    }

    private static void screenCaptureToFile(AbstractBuild build, AndroidDeviceContext device) throws IOException, InterruptedException {
        FilePath screencapFile = build.getWorkspace().createTempFile("screencap", ".png");
        OutputStream screencapStream = screencapFile.write();
        FilterOutputStream replaceFilterStream = new ReplaceFilterOutputStream(screencapStream);
        device.screenshot(replaceFilterStream);
        screencapFile.copyTo(new FilePath(build.getArtifactsDir()).child("screencap.png"));
    }

    private void cleanUp(AbstractBuild build, AndroidDeviceContext device, DeviceFarmApi api, LogcatCollector logcatProcess) throws IOException, InterruptedException {
        if (logcatProcess != null) {
            logcatProcess.saveToFile(KILL_PROCESS_TIMEOUT_MS, ARTIFACT_LOGCAT_TXT);
        }

        if (device != null) {
            screenCaptureToFile(build, device);
            device.disconnect();
        }

        if (api != null) {
            api.disconnect();
        }
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
