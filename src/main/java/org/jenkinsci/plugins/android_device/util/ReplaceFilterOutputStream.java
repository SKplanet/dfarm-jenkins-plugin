package org.jenkinsci.plugins.android_device.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by skyisle on 08/29/2014.
 */

// perl -pe 's/\x0D\x0A/\x0A/g'
public class ReplaceFilterOutputStream extends FilterOutputStream {
    boolean lastHas0x0D;

    public ReplaceFilterOutputStream(OutputStream outputStream) {
        super(outputStream);
        lastHas0x0D = false;
    }

    @Override
    public void write(int i) throws IOException {
        super.write(i);
    }

    @Override
    public void write(byte[] bytes, int offset, int count) throws IOException {
        for (int i = offset; i < offset + count; i++) {
            Byte current = bytes[i];
            if (current == 0x0D) {
                if (i + 1 >= offset + count) {
                    lastHas0x0D = true;
                } else {
                    if (bytes[i + 1] == 0x0A) {
                        continue;
                    } else {
                        write(0x0D);
                    }
                }
            } else if (lastHas0x0D) {
                if (current == 0x0A) {
                    write(current);
                } else {
                    write(0x0D);
                    write(current);
                }
                lastHas0x0D = false;
            } else {
                write(current);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        if (lastHas0x0D) {
            write(0x0D);
            lastHas0x0D = false;
        }

        super.flush();
    }
}
