package org.jenkinsci.plugins.android_device.util;

import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.android_device.SdkInstallationException;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.sdk.Tool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class Utils {

    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());
    private static final Pattern REVISION = Pattern.compile("(\\d++).*");

    /**
     * Gets a combined set of environment variables for the current computer and build.
     *
     * @param build    The build for which we should retrieve environment variables.
     * @param listener The listener used to get the environment variables.
     * @return Environment variables for the current computer, with the build variables taking precedence.
     */
    public static EnvVars getEnvironment(AbstractBuild<?, ?> build, BuildListener listener) {
        final EnvVars envVars = new EnvVars();
        try {
            // Get environment of the local computer
            EnvVars localVars = Computer.currentComputer().getEnvironment();
            envVars.putAll(localVars);

            // Add variables specific to this build
            envVars.putAll(build.getEnvironment(listener));
        } catch (InterruptedException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        }

        return envVars;
    }

    /**
     * Tries to validate the given Android SDK root directory; otherwise tries to
     * locate a copy of the SDK by checking for common environment variables.
     *
     * @param launcher    The launcher for the remote node.
     * @param envVars     Environment variables for the build.
     * @param androidHome The (variable-expanded) SDK root given in global config.
     * @return Either a discovered SDK path or, if all else fails, the given androidHome value.
     */
    public static String discoverAndroidHome(Launcher launcher, Node node,
                                             final EnvVars envVars, final String androidHome) {
        final String autoInstallDir = getSdkInstallDirectory(node).getRemote();

        Callable<String, InterruptedException> task = new Callable<String, InterruptedException>() {
            public String call() throws InterruptedException {
                // Verify existence of provided value

                // Check for common environment variables
                String[] keys = {"ANDROID_SDK_ROOT", "ANDROID_SDK_HOME",
                        "ANDROID_HOME", "ANDROID_SDK"};

                // Resolve each variable to its directory name
                List<String> potentialSdkDirs = new ArrayList<String>();
                for (String key : keys) {
                    potentialSdkDirs.add(envVars.get(key));
                }

                // Also add the auto-installed SDK directory to the list of candidates
                potentialSdkDirs.add(autoInstallDir);

                // Check each directory to see if it's a valid Android SDK
                for (String home : potentialSdkDirs) {
                    return home;
                }

                // Give up
                return null;
            }


            private static final long serialVersionUID = 1L;
        };

        String result = androidHome;
        try {
            result = launcher.getChannel().call(task);
        } catch (InterruptedException e) {
            // Ignore; will return default value
        } catch (IOException e) {
            // Ignore; will return default value
        }
        return result;
    }

    /**
     * Retrieves the path at which the Android SDK should be installed on the current node.
     *
     * @return Path within the tools folder where the SDK should live.
     */
    public static final FilePath getSdkInstallDirectory(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Node is null");
        }

        // Get the root of the node installation
        FilePath root = node.getRootPath();
        if (root == null) {
            throw new IllegalArgumentException("Node " + node.getDisplayName() + " seems to be offline");
        }
        return root.child("tools").child("android-sdk");
    }

    /**
     * Generates a ready-to-use ArgumentListBuilder for one of the Android SDK tools.
     *
     * @param androidSdk The Android SDK to use.
     * @param isUnix     Whether the system where this command should run is sane.
     * @param tool       The Android tool to run.
     * @param args       Any extra arguments for the command.
     * @return Arguments including the full path to the SDK and any extra Windows stuff required.
     */
    public static ArgumentListBuilder getToolCommand(AndroidSdk androidSdk, boolean isUnix, Tool tool, String args) {
        // Determine the path to the desired tool
        String androidToolsDir;
        if (androidSdk.hasKnownRoot()) {
            try {
                androidToolsDir = androidSdk.getSdkRoot() + tool.findInSdk(androidSdk);
            } catch (SdkInstallationException e) {
                LOGGER.warning("A build-tools directory was found but there were no build-tools installed. Assuming command is on the PATH");
                androidToolsDir = "";
            }
        } else {
            LOGGER.warning("SDK root not found. Assuming command is on the PATH");
            androidToolsDir = "";
        }

        // Build tool command
        final String executable = tool.getExecutable(isUnix);
        ArgumentListBuilder builder = new ArgumentListBuilder(androidToolsDir + executable);
        if (args != null) {
            builder.add(Util.tokenize(args));
        }

        return builder;
    }

    /**
     * Expands the variable in the given string to its value in the variables available to this build.
     * The Jenkins-specific build variables take precedence over environment variables.
     *
     * @param envVars   Map of the environment variables.
     * @param buildVars Map of the build-specific variables.
     * @param token     The token which may or may not contain variables in the format <tt>${foo}</tt>.
     * @return The given token, with applicable variable expansions done.
     */
    public static String expandVariables(EnvVars envVars, Map<String, String> buildVars,
                                         String token) {
        final Map<String, String> vars = new HashMap<String, String>(envVars);
        if (buildVars != null) {
            // Build-specific variables, if any, take priority over environment variables
            vars.putAll(buildVars);
        }

        String result = Util.fixEmptyAndTrim(token);
        if (result != null) {
            result = Util.replaceMacro(result, vars);
        }
        return Util.fixEmptyAndTrim(result);
    }

    /**
     * Attempts to kill the given process, timing-out after {@code timeoutMs}.
     *
     * @param process   The process to kill.
     * @param timeoutMs How long to wait for before cancelling the attempt to kill the process.
     * @return {@code true} if the process was killed successfully.
     */
    public static boolean killProcess(final Proc process, final int timeoutMs) {
        Boolean result = null;
        FutureTask<Boolean> task = null;
        try {
            // Attempt to kill the process; remoting will be handled by the process object
            task = new FutureTask<Boolean>(new java.util.concurrent.Callable<Boolean>() {
                public Boolean call() throws Exception {
                    process.kill();
                    return true;
                }
            });

            // Execute the task asynchronously and wait for a result or timeout
            Executors.newSingleThreadExecutor().execute(task);
            result = task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Ignore
        } finally {
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }

        return Boolean.TRUE.equals(result);
    }

    public static boolean isUnix() {
        return File.pathSeparatorChar == ':';
    }
}