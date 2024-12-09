package com.davidvlijmincx.newreader.lib;

import java.lang.foreign.MemorySegment;

public class ReadRequest extends Request {

    ReadRequest(MemorySegment buffer, int fd, LibUringLayer q) {
        super(buffer, fd, q);
    }

    public MemorySegment getData() {
        waitForCompletion();
        return buffer;
    }

    public void freeBuffer(){
        super.freeBuffer();
    }

}
