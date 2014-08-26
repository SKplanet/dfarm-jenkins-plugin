package org.jenkinsci.plugins.android_device;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class AndroidRemoteTest {

    @Test
    public void testValidIpPort() throws Exception {
        checkPort("10.202.30.40", 5412);
        checkPort("192.168.30.40", 5512);
        checkPort("202.30.30.40", 5512);
    }

    @Test
    public void testInvalidIpPort() throws Exception {
        try {
            checkPort("5412.202.30.40", 5412);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    private void checkPort(String ip, int port) throws IOException, InterruptedException {
        AndroidDeviceContext ctx = new AndroidDeviceContext(null, null, null, null, ip, port);
        assertThat(ctx.ip(), is(equalTo(ip)));
        assertThat(ctx.port(), is(equalTo(port)));
    }

    //@Test
    public void testSocketIo() throws Exception {
//
//        Thread thread = new Thread(new Runnable() {
//            public void run() {
//                AndroidRemote remote = new AndroidRemote();
//                try {
//                    remote.connectApiServer();
//                } catch (URISyntaxException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        thread.start();;
//
//        Thread.sleep(10000);
    }
}