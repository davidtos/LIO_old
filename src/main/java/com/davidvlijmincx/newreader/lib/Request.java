package com.davidvlijmincx.newreader.lib;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

abstract class Request {
    CompletableFuture<Void> lock = new CompletableFuture<>();
    MemorySegment buffer;
    int fd;
    JLibUring JLibUring;

    public Request(MemorySegment buffer, int fd, JLibUring JLibUring) {
        this.buffer = buffer;
        this.fd = fd;
        this.JLibUring = JLibUring;
    }

    void dataIsSet() {
        JLibUring.closeFile(fd);
        lock.complete(null);
    }

    void freeBuffer() {
        JLibUring.free(buffer);
    }

    void waitForCompletion(){
        try {
            lock.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
