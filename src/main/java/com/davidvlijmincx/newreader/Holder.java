package com.davidvlijmincx.newreader;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public record Holder(int fd, MemorySegment buffer, ByteBuffer byteBuffer) {

    public Holder(int fd, MemorySegment buffer) {
        this(fd,buffer,null);
    }

}
