package org.jenkinsci.plugins.android_device;

import hudson.FilePath;
import hudson.Proc;
import hudson.model.AbstractBuild;
import org.jenkinsci.plugins.android_device.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
* Created by skyisle on 08/29/2014.
*/
class LogcatCollector {
    private AbstractBuild build;
    private AndroidDeviceContext device;
    private FilePath logcatFile;
    private OutputStream logcatStream;
    private Proc logcatProcess;

    public LogcatCollector(AbstractBuild build, AndroidDeviceContext device) {
        this.build = build;
        this.device = device;
    }

    public void start() throws IOException, InterruptedException {
        logcatFile = build.getWorkspace().createTextTempFile("logcat_", ".log", "", false);
        logcatStream = logcatFile.write();
        logcatProcess = device.startLogcatProc(logcatStream);
    }

    public void saveToFile(int kill_process_time_out_in_ms, String saveFileName) throws IOException, InterruptedException {
        if (logcatProcess != null) {
            if (logcatProcess.isAlive()) {
                // This should have stopped when the emulator was,
                // but if not attempt to kill the process manually.
                // First, give it a final chance to finish cleanly.
                Thread.sleep(3 * 1000);
                if (logcatProcess.isAlive()) {
                    Utils.killProcess(logcatProcess, kill_process_time_out_in_ms);
                }
            }
            try {
                logcatStream.close();
            } catch (Exception ignore) {
            }

            // Archive the logs
            if (logcatFile.length() != 0) {
                logcatFile.copyTo(new FilePath(build.getArtifactsDir()).child(saveFileName));
            }
            logcatFile.delete();
        }
    }


}
