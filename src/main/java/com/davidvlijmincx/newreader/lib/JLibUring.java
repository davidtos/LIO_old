package com.davidvlijmincx.newreader.lib;


import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class JLibUring implements AutoCloseable {

    private final Map<Integer, Request> requests = new ConcurrentHashMap<>();
    private final LibUringLayer LibUringLayer;
    private int userData = 0;


    public JLibUring(int queueDepth, boolean polling) {
        this.LibUringLayer = new LibUringLayer(queueDepth, polling);
        startPoller();
    }

    void startPoller() {
        Thread.ofPlatform().daemon(true).start(() -> {
            while (true) {
                final int userData = LibUringLayer.waitAndSee();

                Request request = requests.get(userData);
                while (request == null) {
                    request = requests.get(userData);
                }

                request.dataIsSet();
                requests.remove(userData);

            }
        });
    }

    public void submit() {
        LibUringLayer.submit();
    }

    public ReadRequest submitRead(String path, int size, int offset) {
        userData++;
        final int fd = LibUringLayer.open(path, 0, 0);
        final MemorySegment buffer = LibUringLayer.prepareReadRequest(fd, size, userData, offset);
        ReadRequest dataHolder = new ReadRequest(buffer, fd, LibUringLayer);
        requests.put(userData, dataHolder);
        return dataHolder;
    }

    public WriteRequest prepareWrite(String path, byte[] content, int offset) {
        userData++;
        int fd = LibUringLayer.open(path, 0, 2);
        MemorySegment writeSegment = LibUringLayer.malloc(content.length);
        MemorySegment.copy(content, 0, writeSegment, JAVA_BYTE, 0, content.length);
        LibUringLayer.prepareWriteRequest(userData, fd, writeSegment, offset);
        WriteRequest dataHolder = new WriteRequest(writeSegment, fd, LibUringLayer);
        requests.put(userData, dataHolder);
        return dataHolder;
    }

    @Override
    public void close() {
        LibUringLayer.close();
    }
}
