package com.davidvlijmincx.newreader.lib;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

abstract class Request {
    private final CompletableFuture<Void> lock = new CompletableFuture<>();
    final MemorySegment buffer;
    private final  int fd;
    private final LibUringLayer LibUringLayer;

    Request(MemorySegment buffer, int fd, LibUringLayer LibUringLayer) {
        this.buffer = buffer;
        this.fd = fd;
        this.LibUringLayer = LibUringLayer;
    }

    void dataIsSet() {
        LibUringLayer.closeFile(fd);
        lock.complete(null);
    }

    void freeBuffer() {
        LibUringLayer.free(buffer);
    }

    void waitForCompletion(){
        try {
            lock.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
