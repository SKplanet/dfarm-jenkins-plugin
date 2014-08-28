package org.jenkinsci.plugins.android_device.sdk;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.android_device.SdkInstallationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by skyisle on 08/28/2014.
 */
public class SdkUtils {

    private static final Logger LOGGER = Logger.getLogger(SdkUtils.class.getName());

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
}
