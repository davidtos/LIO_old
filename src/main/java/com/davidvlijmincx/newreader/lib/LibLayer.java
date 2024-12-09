package com.davidvlijmincx.newreader.lib;


import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class LibLayer implements AutoCloseable {

    private final Map<Integer, Request> requests = new ConcurrentHashMap<>();
    private final JLibUring JLibUring;
    private int userData = 0;


    public LibLayer(int queueDepth, boolean polling) {
        this.JLibUring = new JLibUring(queueDepth, polling);
        startPoller();
    }

    public void startPoller() {
        Thread.ofPlatform().daemon(true).start(() -> {
            while (true) {
                final int userData = JLibUring.waitAndSee();

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
        JLibUring.submit();
    }

    public ReadRequest submitRead(String path, int size, int offset) {
        userData++;
        final int fd = JLibUring.open(path, 0, 0);
        final MemorySegment buffer = JLibUring.prepareReadRequest(fd, size, userData, offset);
        ReadRequest dataHolder = new ReadRequest(buffer, fd, JLibUring);
        requests.put(userData, dataHolder);
        return dataHolder;
    }

    public WriteRequest prepareWrite(String path, byte[] content, int offset) {
        userData++;
        int fd = JLibUring.open(path, 0, 2);
        MemorySegment writeSegment = JLibUring.malloc(content.length);
        MemorySegment.copy(content, 0, writeSegment, JAVA_BYTE, 0, content.length);
        JLibUring.prepareWriteRequest(userData, fd, writeSegment, offset);
        WriteRequest dataHolder = new WriteRequest(writeSegment, fd, JLibUring);
        requests.put(userData, dataHolder);
        return dataHolder;
    }

    @Override
    public void close() {
        JLibUring.close();
    }
}
