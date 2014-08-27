package org.jenkinsci.plugins.android_device.api;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.jenkinsci.plugins.android_device.FailedToConnectApiServerException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class DeviceFarmApiTest {
    private static final int PORT = 9099;
    private static final String HOST = "localhost";
    private SocketIOServer server;
    private StringBuffer jenOutData = new StringBuffer();

    @Before
    public void setUp() throws Exception {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(PORT);

        server = new SocketIOServer(config);

        server.addEventListener(DeviceFarmApi.KEY_JEN_DEVICE, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String jenDevice, AckRequest ackRequest) throws Exception {
                socketIOClient.sendEvent(DeviceFarmApi.KEY_SVC_DEVICE, "{\"ip\":\"10.20.30.40\",\"port\":\"8888\",\"tag\":\"TEST-365\"}");
            }
        });

        server.addEventListener(DeviceFarmApi.KEY_JEN_OUT, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String outData, AckRequest ackRequest) throws Exception {
                jenOutData.append(outData);
            }
        });

        server.start();
        jenOutData.setLength(0);
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testInvalidApiServerHostPort() throws Exception {
        try {
            connect("aaa$$)#:1234");
            fail();
        } catch (FailedToConnectApiServerException e) {
        }
    }

    private DeviceFarmApi connect(String url) throws FailedToConnectApiServerException {
        DeviceFarmApi api = new DeviceFarmApi();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logger = new PrintStream(outputStream);
        StringBuffer buffer = new StringBuffer();
        api.connectApiServer(logger, buffer, url, "", "Job#1");
        return api;
    }

    @Test
    public void testDisconnectApiServer() throws Exception, FailedToConnectApiServerException {
        DeviceFarmApi api = connect(String.format("http://%s:%d", HOST, PORT));
        api.disconnect();

        long start = System.currentTimeMillis();
        int timeout_in_ms = 5000;
        while(start + timeout_in_ms > System.currentTimeMillis()) {
            synchronized (jenOutData) {
                if(jenOutData.length() != 0) {
                    break;
                }
            }

            Thread.sleep(100);
        }
        assertThat(jenOutData.toString(), is(equalTo("bye")));
    }
}