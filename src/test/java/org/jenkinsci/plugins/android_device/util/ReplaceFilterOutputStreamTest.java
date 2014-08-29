package org.jenkinsci.plugins.android_device.util;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ReplaceFilterOutputStreamTest {

    private ReplaceFilterOutputStream filterOutputStream;
    private ByteArrayOutputStream stream;

    @Before
    public void setUp() throws Exception {
        stream = new ByteArrayOutputStream();
        filterOutputStream = new ReplaceFilterOutputStream(stream);
    }

    @Test
    public void testCheckReplace() throws Exception {

        filterOutputStream.write(new byte[]{0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(4));
    }

    @Test
    public void testCheckReplaceWithLast0x0D() throws Exception {

        filterOutputStream.write(new byte[]{0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA, 0xD});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(5));

    }

    @Test
    public void testCheckReplaceWithMiddle() throws Exception {

        filterOutputStream.write(new byte[]{0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA, 0xD}, 1, 3);
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(2));

    }

    @Test
    public void testCheckNoReplacementWithNoData() throws Exception {

        filterOutputStream.write(new byte[]{});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(0));

    }


    @Test
    public void testCheckReplaceWithAllReplace() throws Exception {

        filterOutputStream.write(new byte[]{0xD, 0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA, 0xD, 0xA});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(6));

    }

    @Test
    public void testCheckReplaceWithMultipleWrite() throws Exception {

        filterOutputStream.write(new byte[]{0xD});
        filterOutputStream.write(new byte[]{0xA});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(1));

    }

    @Test
    public void testCheckNoReplaceWithMultipleWrite() throws Exception {

        filterOutputStream.write(new byte[]{0xD});
        filterOutputStream.write(new byte[]{0xC});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(2));

    }

    @Test
    public void testCheckReplaceWithPngHeader() throws Exception {

        filterOutputStream.write(new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0d, 0x0a, 0x1a,
                0x0d, 0x0a, 0x00, 0x00,
                0x00, 0x0d, 0x49, 0x48});
        filterOutputStream.flush();

        assertThat(stream.toByteArray().length, is(14));

    }
}