package com.davidvlijmincx.newreader;

import java.lang.foreign.MemorySegment;

public record Holder(MemorySegment fd, MemorySegment buffer) {
}
