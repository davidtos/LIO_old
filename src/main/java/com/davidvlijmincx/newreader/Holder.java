package com.davidvlijmincx.newreader;

import java.lang.foreign.MemorySegment;

public record Holder(int fd, MemorySegment buffer) {
}
