package org.jenkinsci.plugins.android_device.api;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.jenkinsci.plugins.android_device.FailedToConnectApiServerException;
import org.jenkinsci.plugins.android_device.RemoteDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DeviceFarmApiTest {
    public static final int DEFAULT_CONNECT_TIMEOUT = 1000;
    private static int PORT = 10067;
    public static final int INVALID_PORT = 9876;
    private static final String HOST = "localhost";
    public static final String DEVICE_HOST = "10.20.30.40";
    public static final int DEVICE_PORT = 8888;
    private SocketIOServer server;
    private StringBuffer jenOutData = new StringBuffer();
    private CountDownLatch countDownLatch;


    private static final Logger LOGGER = Logger.getLogger(DeviceFarmApiTest.class.getName());

    @Before
    public void setUp() throws Exception {
        Configuration config = new Configuration();
        config.setHostname("localhost");
        config.setPort(PORT);
        config.setAckMode(AckMode.MANUAL);

        countDownLatch = new CountDownLatch(1);

        server = new SocketIOServer(config);

        server.addEventListener(DeviceFarmApi.KEY_JEN_OUT, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String outData, AckRequest ackRequest) throws Exception {
                jenOutData.append(outData);
                LOGGER.warning("jen_out received");
            }
        });

        server.addDisconnectListener(new DisconnectListener() {
            public void onDisconnect(SocketIOClient client) {
                countDownLatch.countDown();
                LOGGER.warning("onDisconnect received");
            }
        });

        server.start();
        jenOutData.setLength(0);
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        PORT += 10;
    }

    private DeviceFarmApi connect(String url, int connectTimeout) throws FailedToConnectApiServerException {
        DeviceFarmApi api = new DeviceFarmApiImpl();
        api.connectApiServer(logger(), url, "", "Job#1", connectTimeout);
        return api;
    }

    private PrintStream logger() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        return new PrintStream(outputStream);
    }

    @Test
    public void testInvalidApiServerHostPort() throws Exception {
        try {
            connect("aaa$$)#:1234", DEFAULT_CONNECT_TIMEOUT);
            fail();
        } catch (FailedToConnectApiServerException e) {
        }
    }

    private void waitDisconnect() throws InterruptedException {
        while (countDownLatch.getCount() == 1) {
            countDownLatch.await(500, TimeUnit.MILLISECONDS);
            LOGGER.info("wait " + Thread.currentThread().getId());
        }

        LOGGER.info("countDownLatch = " + countDownLatch.getCount());
    }

    @Test
    public void testResponseOk() throws Exception, FailedToConnectApiServerException, MalformedResponseException, NoDeviceAvailableException {
        server.addEventListener(DeviceFarmApi.KEY_JEN_DEVICE, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String jenDevice, AckRequest ackRequest) throws Exception {
                socketIOClient.sendEvent(DeviceFarmApi.KEY_SVC_DEVICE, "{\"ip\":\"" + DEVICE_HOST + "\",\"port\":\"" + DEVICE_PORT + "\",\"tag\":\"TEST-365\"}");
            }
        });

        DeviceFarmApi api = connect(String.format("http://%s:%d", HOST, PORT), DEFAULT_CONNECT_TIMEOUT);
        RemoteDevice remoteDevice = api.waitApiResponse(logger(), 7000, 5000);

        assertThat(remoteDevice.ip, is(equalTo(DEVICE_HOST)));
        assertThat(remoteDevice.port, is(equalTo(DEVICE_PORT)));
        api.disconnect();
        waitDisconnect();
    }

    @Test
    public void testResponseTimeoutException() throws Exception, FailedToConnectApiServerException, MalformedResponseException, NoDeviceAvailableException {
        DeviceFarmApi api = connect(String.format("http://%s:%d", HOST, PORT), DEFAULT_CONNECT_TIMEOUT);

        try {
            api.waitApiResponse(logger(), 2000, 1000);
            fail();
        } catch (TimeoutException e) {
        }

        api.disconnect();
        waitDisconnect();
    }

    @Ignore
    public void testConnectionFailedTimeoutException() throws FailedToConnectApiServerException, MalformedResponseException, TimeoutException, NoDeviceAvailableException {
        DeviceFarmApi api = connect(String.format("http://%s:%d", HOST, INVALID_PORT), 1000);

        try {
            api.waitApiResponse(logger(), 10000, 500);
            fail();
        } catch (FailedToConnectApiServerException e) {
        }

        api.disconnect();
    }


    @Test
    public void testMalformedResponseException() throws Exception, FailedToConnectApiServerException, NoDeviceAvailableException {
        server.addEventListener(DeviceFarmApi.KEY_JEN_DEVICE, String.class, new DataListener<String>() {
            public void onData(SocketIOClient socketIOClient, String jenDevice, AckRequest ackRequest) throws Exception {
                socketIOClient.sendEvent(DeviceFarmApi.KEY_SVC_DEVICE, "{\"ip\":\"" + DEVICE_HOST + "\",\"poXXXXrt\":\"" + DEVICE_PORT + "\",\"tag\":\"TEST-365\"}");
            }
        });
        DeviceFarmApi api = connect(String.format("http://%s:%d", HOST, PORT), DEFAULT_CONNECT_TIMEOUT);

        try {
            api.waitApiResponse(logger(), 7000, 5000);
            fail();
        } catch (MalformedResponseException e) {
        }

        api.disconnect();
        waitDisconnect();
    }
}