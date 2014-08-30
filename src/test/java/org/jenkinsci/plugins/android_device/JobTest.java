package org.jenkinsci.plugins.android_device;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildWrapper;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.android_device.api.DeviceFarmApiImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by skyisle on 08/27/2014.
 */
public class JobTest {
    public static int PORT = 9089;

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private SocketIOServer server;

    @Before
    public void setUp() throws Exception {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(PORT);

        server = new SocketIOServer(config);

        server.addEventListener(DeviceFarmApiImpl.KEY_JEN_DEVICE, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String jenDevice, AckRequest ackRequest) throws Exception {
                socketIOClient.sendEvent(DeviceFarmApiImpl.KEY_SVC_DEVICE, "{\"ip\":\"10.20.30.40\",\"port\":\"8888\",\"tag\":\"TEST-365\"}");
            }
        });

        server.addEventListener(DeviceFarmApiImpl.KEY_JEN_OUT, Object.class, new DataListener<Object>() {
            public void onData(SocketIOClient socketIOClient, Object jenDevice, AckRequest ackRequest) throws Exception {

            }
        });
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void first() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                                   BuildListener listener) throws InterruptedException, IOException {

                AndroidRemote androidRemote = new AndroidRemote("http://localhost:" + PORT, "") {

                };
                BuildWrapper.Environment environment = androidRemote.setUp(build, launcher, listener);
                if (environment != null) {
                    environment.tearDown(build, listener);
                }
                return true;
            }
        });

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("adb -s 10.20.30.40:8888 logcat -v time"));
    }

}
