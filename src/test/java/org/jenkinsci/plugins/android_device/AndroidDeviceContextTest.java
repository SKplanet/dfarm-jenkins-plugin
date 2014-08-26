package org.jenkinsci.plugins.android_device;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AndroidDeviceContextTest {
//
//    @Rule
//    public JenkinsRule j = new JenkinsRule();

    //@Test
    public void first() throws Exception {
//        FreeStyleProject project = j.createFreeStyleProject();
//        project.getBuildersList().add(new Shell("echo hello"));
//        FreeStyleBuild build = project.scheduleBuild2(0).get();
//        System.out.println(build.getDisplayName() + " completed");
//        String s = FileUtils.readFileToString(build.getLogFile());
//        assertThat(s, containsString("+ echo hello"));
    }

    @Test
    public void testControlMethods() throws Exception {
        String ip = "10.203.202.178";
        int port = 5555;
        final String[] called = new String[1];

        AndroidDeviceContext deviceMock = new AndroidDeviceContext(null, null, null, null, ip, port) {
            @Override
            public void sendCommand(String command, int timeout) throws IOException, InterruptedException {
                called[0] = command;
            }

            @Override
            public void sendCommandWithSerial(String command, int timeout_in_ms) throws IOException, InterruptedException {
                called[0] = command;
            }
        };

        deviceMock.connect(5000);
        assertThat(called[0], is(equalTo(String.format("connect %s:%d", ip, port))));

        deviceMock.powerOn();
        assertThat(called[0], is(equalTo("input keyevent 26")));

        deviceMock.unlockScreen();
        assertThat(called[0], is(equalTo("input keyevent 82")));

        deviceMock.disconnect();
        assertThat(called[0], is(equalTo(String.format("disconnect %s:%d", ip, port))));

    }
}