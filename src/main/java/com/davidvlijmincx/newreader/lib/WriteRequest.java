package com.davidvlijmincx.newreader.lib;

import java.lang.foreign.MemorySegment;

public class WriteRequest extends Request {

    public WriteRequest(MemorySegment buffer, int fd, JLibUring q) {
        super(buffer, fd, q);
    }

    void dataIsSet() {
        super.dataIsSet();
        freeBuffer();
    }

    public void waitForWriteToFinish() {
        waitForCompletion();
    }
}
