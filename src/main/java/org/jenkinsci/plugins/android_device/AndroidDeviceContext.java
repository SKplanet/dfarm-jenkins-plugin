package org.jenkinsci.plugins.android_device;

import com.google.common.net.InetAddresses;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.NullStream;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.sdk.SdkUtils;
import org.jenkinsci.plugins.android_device.sdk.Tool;
import org.jenkinsci.plugins.android_device.util.Utils;

import java.io.*;
import java.util.concurrent.TimeUnit;

import static org.jenkinsci.plugins.android_device.AndroidRemote.log;

/**
 * Created by skyisle on 08/25/2014.
 */
public class AndroidDeviceContext {
    private static final int DEFAULT_COMMAND_TIMEOUT_MS = 15000;
    public static final int KEY_MENU = 82;
    public static final int KEY_POWER = 26;
    private String ip;
    private int port;

    private AndroidSdk sdk;

    private AbstractBuild<?, ?> build;
    private BuildListener listener;
    private Launcher launcher;


    public AndroidDeviceContext(AbstractBuild<?, ?> build_,
                                Launcher launcher_, BuildListener listener_, AndroidSdk sdk_, String ip, int port)
            throws InterruptedException, IOException {

        build = build_;
        listener = listener_;
        launcher = launcher_;
        sdk = sdk_;

        InetAddresses.forString(ip);
        this.ip = ip;
        this.port = port;
    }

    public String ip() {
        return this.ip;
    }

    public int port() {
        return this.port;
    }

    /**
     * Sets up a standard {@link hudson.Launcher.ProcStarter} for the current context.
     *
     * @return A ready ProcStarter
     * @throws IOException
     * @throws InterruptedException
     */
    private Launcher.ProcStarter getProcStarter() throws IOException, InterruptedException {
        final EnvVars buildEnvironment = build.getEnvironment(TaskListener.NULL);
        sdk.setupEnvVars(buildEnvironment);
        return launcher.launch().stdout(new NullStream()).stderr(logger()).envs(buildEnvironment);
    }

    private PrintStream logger() {
        return listener.getLogger();
    }

    /**
     * Sets up a standard {@link hudson.Launcher.ProcStarter} for the current adb environment,
     * ready to execute the given command.
     *
     * @param command What command to run
     * @return A ready ProcStarter
     * @throws IOException
     * @throws InterruptedException
     */
    public Launcher.ProcStarter getProcStarter(ArgumentListBuilder command)
            throws IOException, InterruptedException {
        return getProcStarter().cmds(command);
    }

    /**
     * Generates a ready-to-use ArgumentListBuilder for one of the Android SDK tools, based on the current context.
     *
     * @param tool The Android tool to run.
     * @param args Any extra arguments for the command.
     * @return Arguments including the full path to the SDK and any extra Windows stuff required.
     */
    public ArgumentListBuilder getToolCommand(Tool tool, String args) {
        return SdkUtils.getToolCommand(sdk, File.pathSeparatorChar == ':', tool, args);
    }

    /**
     * Generates a ready-to-use ProcStarter for one of the Android SDK tools, based on the current context.
     *
     * @param tool The Android tool to run.
     * @param args Any extra arguments for the command.
     * @return A ready ProcStarter
     * @throws IOException
     * @throws InterruptedException
     */
    public Launcher.ProcStarter getToolProcStarter(Tool tool, String args)
            throws IOException, InterruptedException {
        return getProcStarter(SdkUtils.getToolCommand(sdk, Utils.isUnix(), tool, args));
    }

    public String serial() {
        return String.format("%s:%d", ip, port);
    }

    void unlockScreen() throws IOException, InterruptedException {
        sendKey(KEY_MENU);
    }

    void powerOn() throws IOException, InterruptedException {
        sendKey(KEY_POWER);
    }

    private void sendKey(int keyCode) throws IOException, InterruptedException {
        final String keyEventArgs = String.format("input keyevent %d", keyCode);
        sendCommandWithSerial(keyEventArgs, DEFAULT_COMMAND_TIMEOUT_MS);
    }

    public void connect(int timeout_in_millis)
            throws IOException, InterruptedException {
        String args = "connect " + serial();
        sendCommand(args, timeout_in_millis);
    }

    public void disconnect()
            throws IOException, InterruptedException {
        final String args = "disconnect " + serial();
        sendCommand(args, DEFAULT_COMMAND_TIMEOUT_MS);
    }

    public void devices()
            throws IOException, InterruptedException {
        String args = "devices";
        sendCommand(args, DEFAULT_COMMAND_TIMEOUT_MS);
    }

    Proc startLogcatProc(OutputStream logcatStream) throws IOException, InterruptedException {
        final String logcatArgs = String.format("-s %s logcat -v time", serial());
        return getToolProcStarter(Tool.ADB, logcatArgs).stdout(logcatStream).stderr(new NullStream()).start();
    }

    public void sendCommand(String command, int timeout) throws IOException, InterruptedException {
        ArgumentListBuilder adbConnectCmd = getToolCommand(Tool.ADB, command);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        getProcStarter(adbConnectCmd).stdout(outputStream).start().joinWithTimeout(timeout, TimeUnit.MILLISECONDS, listener);

        log(logger(), outputStream.toString());
    }

    public void sendCommandWithSerial(String command, int timeout_in_ms) throws IOException, InterruptedException {
        final String commandArgs = String.format("-s %s shell %s", serial(), command);
        ArgumentListBuilder adbConnectCmd = getToolCommand(Tool.ADB, commandArgs);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        getProcStarter(adbConnectCmd).stdout(outputStream).start().joinWithTimeout(timeout_in_ms, TimeUnit.MILLISECONDS, listener);

        log(logger(), outputStream.toString());
    }
}
