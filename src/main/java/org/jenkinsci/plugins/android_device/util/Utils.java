package org.jenkinsci.plugins.android_device.util;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.android_device.Messages;
import org.jenkinsci.plugins.android_device.SdkInstallationException;
import org.jenkinsci.plugins.android_device.sdk.AndroidSdk;
import org.jenkinsci.plugins.android_device.sdk.Tool;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.android_device.AndroidRemote.log;


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
     * Detects the root directory of an SDK installation based on the Android tools on the PATH.
     *
     * @param isUnix Whether the system where this command should run is sane.
     * @return The root directory of an Android SDK, or {@code null} if none could be determined.
     */
    private static String getSdkRootFromPath(boolean isUnix, String systemPath) {
        // List of tools which should be found together in an Android SDK tools directory
        Tool[] tools = {Tool.ANDROID};

        // Get list of directories from the PATH environment variable
        List<String> paths = Arrays.asList(systemPath.split(File.pathSeparator));

        // Examine each directory to see whether it contains the expected Android tools
        for (String path : paths) {
            File toolsDir = new File(path);
            if (!toolsDir.exists() || !toolsDir.isDirectory()) {
                continue;
            }

            int toolCount = 0;
            for (Tool tool : tools) {
                String executable = tool.getExecutable(isUnix);
                if (new File(toolsDir, executable).exists()) {
                    toolCount++;
                }
            }

            // If all the tools were found in this directory, we have a winner
            if (toolCount == tools.length) {
                // Return the parent path (i.e. the SDK root)
                return toolsDir.getParent();
            }
        }

        return null;
    }

    /**
     * Tries to validate the given Android SDK root directory; otherwise tries to
     * locate a copy of the SDK by checking for common environment variables.
     *
     * @param launcher The launcher for the remote node.
     * @param envVars Environment variables for the build.
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
                String[] keys = { "ANDROID_SDK_ROOT", "ANDROID_SDK_HOME",
                        "ANDROID_HOME", "ANDROID_SDK" };

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
     * Runs an Android tool on the remote build node and waits for completion before returning.
     *
     * @param launcher         The launcher for the remote node.
     * @param stdout           The stream to which standard output should be redirected.
     * @param stderr           The stream to which standard error should be redirected.
     * @param androidSdk       The Android SDK to use.
     * @param tool             The Android tool to run.
     * @param args             Any extra arguments for the command.
     * @param workingDirectory The directory to run the tool from, or {@code null} if irrelevant
     * @throws IOException          If execution of the tool fails.
     * @throws InterruptedException If execution of the tool is interrupted.
     */
    public static void runAndroidTool(Launcher launcher, OutputStream stdout, OutputStream stderr,
                                      AndroidSdk androidSdk, Tool tool, String args, FilePath workingDirectory)
            throws IOException, InterruptedException {
        runAndroidTool(launcher, new EnvVars(), stdout, stderr, androidSdk, tool, args, workingDirectory);
    }

    public static void runAndroidTool(Launcher launcher, EnvVars env, OutputStream stdout, OutputStream stderr,
                                      AndroidSdk androidSdk, Tool tool, String args, FilePath workingDirectory)
            throws IOException, InterruptedException {

        ArgumentListBuilder cmd = Utils.getToolCommand(androidSdk, launcher.isUnix(), tool, args);
        ProcStarter procStarter = launcher.launch().stdout(stdout).stderr(stderr).cmds(cmd);
        if (androidSdk.hasKnownHome()) {
            // Copy the old one, so we don't mutate the argument.
            env = new EnvVars((env == null ? new EnvVars() : env));
            env.put("ANDROID_SDK_HOME", androidSdk.getSdkHome());
        }

        if (env != null) {
            procStarter = procStarter.envs(env);
        }

        if (workingDirectory != null) {
            procStarter.pwd(workingDirectory);
        }
        procStarter.join();
    }

    /**
     * Expands the variable in the given string to its value in the environment variables available
     * to this build.  The Jenkins-specific build variables for this build are then substituted.
     *
     * @param build    The build from which to get the build-specific and environment variables.
     * @param listener The listener used to get the environment variables.
     * @param token    The token which may or may not contain variables in the format <tt>${foo}</tt>.
     * @return The given token, with applicable variable expansions done.
     */
    public static String expandVariables(AbstractBuild<?, ?> build, BuildListener listener, String token) {
        EnvVars envVars;
        Map<String, String> buildVars;

        try {
            EnvVars localVars = Computer.currentComputer().getEnvironment();
            envVars = new EnvVars(localVars);
            envVars.putAll(build.getEnvironment(listener));
            buildVars = build.getBuildVariables();
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            return null;
        }

        return expandVariables(envVars, buildVars, token);
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

    /**
     * Determines the relative path required to get from one path to another.
     *
     * @param from Path to go from.
     * @param to   Path to reach.
     * @return The relative path between the two, or {@code null} for invalid input.
     */
    public static String getRelativePath(String from, String to) {
        // Check for bad input
        if (from == null || to == null) {
            return null;
        }

        String fromPath, toPath;
        try {
            fromPath = new File(from).getCanonicalPath();
            toPath = new File(to).getCanonicalPath();
        } catch (IOException e1) {
            e1.printStackTrace();
            return null;
        }

        // Nothing to do if the two are equal
        if (fromPath.equals(toPath)) {
            return "";
        }
        // Target directory is a subdirectory
        if (toPath.startsWith(fromPath)) {
            int fromLength = fromPath.length();
            int index = fromLength == 1 ? 1 : fromLength + 1;
            return toPath.substring(index) + File.separatorChar;
        }

        // Quote separator, as String.split() takes a regex and
        // File.separator isn't a valid regex character on Windows
        final String separator = Pattern.quote(File.separator);
        // Target directory is somewhere above our directory
        String[] fromParts = fromPath.substring(1).split(separator);
        final int fromLength = fromParts.length;
        String[] toParts = toPath.substring(1).split(separator);
        final int toLength = toParts.length;

        // Find the number of common path segments
        int commonLength = 0;
        for (int i = 0; i < toLength; i++) {
            if (fromParts[i].length() == 0) {
                continue;
            }
            if (!fromParts[i].equals(toParts[i])) {
                break;
            }
            commonLength++;
        }

        // Determine how many directories up we need to go
        int diff = fromLength - commonLength;
        StringBuilder rel = new StringBuilder();
        for (int i = 0; i < diff; i++) {
            rel.append("..");
            rel.append(File.separatorChar);
        }

        // Add on the remaining path segments to the target
        for (int i = commonLength; i < toLength; i++) {
            rel.append(toParts[i]);
            rel.append(File.separatorChar);
        }

        return rel.toString();
    }

    /**
     * Determines the number of steps required to get between two paths.
     * <p/>
     * e.g. To get from "/foo/bar/baz" to "/foo/blah" requires making three steps:
     * <ul>
     * <li>"/foo/bar"</li>
     * <li>"/foo"</li>
     * <li>"/foo/blah"</li>
     * </ul>
     *
     * @param from Path to go from.
     * @param to   Path to reach.
     * @return The relative distance between the two, or {@code -1} for invalid input.
     */
    public static int getRelativePathDistance(String from, String to) {
        final String relative = getRelativePath(from, to);
        if (relative == null) {
            return -1;
        }

        final String[] parts = relative.split("/");
        final int length = parts.length;
        if (length == 1 && parts[0].isEmpty()) {
            return 0;
        }
        return parts.length;
    }

    public static int parseRevisionString(String revisionStr) {
        try {
            return Integer.parseInt(revisionStr);
        } catch (NumberFormatException e) {
            Matcher matcher = REVISION.matcher(revisionStr);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
            throw new NumberFormatException("Could not parse " + revisionStr);
        }
    }

    /**
     * Determines the API level for the given platform name.
     *
     * @param platform String like "android-4" or "Google:Google APIs:14".
     * @return The detected version, or {@code -1} if not determined.
     */
    public static int getApiLevelFromPlatform(String platform) {
        int apiLevel = -1;
        platform = Util.fixEmptyAndTrim(platform);
        if (platform == null) {
            return apiLevel;
        }

        Matcher matcher = Pattern.compile("[-:]([0-9]{1,2})$").matcher(platform);
        if (matcher.find()) {
            String end = matcher.group(1);
            try {
                apiLevel = Integer.parseInt(end);
            } catch (NumberFormatException e) {
            }
        }
        return apiLevel;
    }

    public static boolean isUnix() {
        return File.pathSeparatorChar == ':';
    }

    /**
     * Task that will execute a command on the given emulator's console port, then quit.
     */
    private static final class EmulatorCommandTask implements Callable<Boolean, IOException> {

        private final int port;
        private final String command;

        @SuppressWarnings("hiding")
        EmulatorCommandTask(int port, String command) {
            this.port = port;
            this.command = command;
        }

        @SuppressWarnings("null")
        public Boolean call() throws IOException {
            Socket socket = null;
            BufferedReader in = null;
            PrintWriter out = null;
            try {
                // Connect to the emulator's console port
                socket = new Socket("127.0.0.1", port);
                out = new PrintWriter(socket.getOutputStream());
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // If we didn't get a banner response, give up
                if (in.readLine() == null) {
                    return false;
                }

                // Send command, then exit the console
                out.write(command);
                out.write("\r\n");
                out.flush();
                out.write("quit\r\n");
                out.flush();

                // Wait for the commands to return a response
                while (in.readLine() != null) {
                    // Ignore
                }
            } finally {
                try {
                    out.close();
                } catch (Exception ignore) {
                }
                try {
                    in.close();
                } catch (Exception ignore) {
                }
                try {
                    socket.close();
                } catch (Exception ignore) {
                }
            }

            return true;
        }

        private static final long serialVersionUID = 1L;
    }

}