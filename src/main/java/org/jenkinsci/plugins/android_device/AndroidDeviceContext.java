package org.jenkinsci.plugins.android_device;

import com.google.common.net.InetAddresses;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.NullStream;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.sdk.Tool;
import org.jenkinsci.plugins.android_device.util.Utils;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by skyisle on 08/25/2014.
 */
public class AndroidDeviceContext {
    private static final int EMULATOR_COMMAND_TIMEOUT_MS = 15000;
    private String ip;
    private int port;

    private AndroidSdk sdk;

    private AbstractBuild<?, ?> build;
    private BuildListener listener;
    private Launcher launcher;
    private int adbServerPort;
    private int userPort;


    public AndroidDeviceContext(String ip, int port) {
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
    public Launcher.ProcStarter getProcStarter() throws IOException, InterruptedException {
        final EnvVars buildEnvironment = build.getEnvironment(TaskListener.NULL);
        buildEnvironment.put("ANDROID_ADB_SERVER_PORT", Integer.toString(adbServerPort));
        if (sdk.hasKnownHome()) {
            buildEnvironment.put("ANDROID_SDK_HOME", sdk.getSdkHome());
        }
        if (launcher.isUnix()) {
            buildEnvironment.put("LD_LIBRARY_PATH", String.format("%s/tools/lib", sdk.getSdkRoot()));
        }
        return launcher.launch().stdout(new NullStream()).stderr(logger()).envs(buildEnvironment);
    }

    private PrintStream logger() {
        return null;
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
        return Utils.getToolCommand(sdk, launcher.isUnix(), tool, args);
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
        return getProcStarter(Utils.getToolCommand(sdk, launcher.isUnix(), tool, args));
    }

    /**
     * Sends a user command to the running emulator via its telnet interface.<br>
     * Execution will be cancelled if it takes longer than
     * {@link #EMULATOR_COMMAND_TIMEOUT_MS}.
     *
     * @param command The command to execute on the emulator's telnet interface.
     * @return Whether sending the command succeeded.
     */
    public boolean sendCommand(final String command) {
        return sendCommand(command, EMULATOR_COMMAND_TIMEOUT_MS);
    }

    /**
     * Sends a user command to the running emulator via its telnet interface.<br>
     * Execution will be cancelled if it takes longer than timeout ms.
     *
     * @param command The command to execute on the emulator's telnet interface.
     * @param timeout The command's timeout, in ms.
     * @return Whether sending the command succeeded.
     */
    public boolean sendCommand(final String command, int timeout) {
        return Utils.sendEmulatorCommand(launcher, logger(), userPort, command, timeout);
    }
}
