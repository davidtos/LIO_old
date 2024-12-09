package com.davidvlijmincx.newreader.lib;

import java.lang.foreign.MemorySegment;

public class WriteRequest extends Request {

    WriteRequest(MemorySegment buffer, int fd, LibUringLayer q) {
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
