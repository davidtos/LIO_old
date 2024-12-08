package com.davidvlijmincx.newreader;


import com.davidvlijmincx.setup.BenchmarkFiles;
import com.davidvlijmincx.setup.FileTooReadData;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.StampedLock;


public class VtTester {

    static Map<Integer, DataHolder> requests = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {

        VtTester main = new VtTester();
        FileTooReadData[] filesTooRead = BenchmarkFiles.filesTooRead;

        try (var q = new QuickReader(filesTooRead.length, true)) {

            Thread thread = Thread.ofPlatform().start(() -> {
                for (int i = 0; i < filesTooRead.length; i++) {
                    System.out.println("i = " + i);
                    final int userData = q.waitAndSee();

                    DataHolder dataHolder = requests.get(userData);
                    while (dataHolder == null) {
                        dataHolder = requests.get(userData);
                    }

                    dataHolder.dataIsSet();

                }
            });

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < filesTooRead.length; i++) {
                    DataHolder dataHolder = main.submitRead(q, filesTooRead[i], i);
                    executor.execute(() -> {
                        dataHolder.getData();
                        dataHolder.freeBuffer();
                    });
                }
            }

            thread.join();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    DataHolder submitRead(QuickReader q, FileTooReadData path, int userData) {
        final int fd = q.open(path.sPath());
        final MemorySegment buffer = q.submitReadRequest2(fd, path.bufferSize(), userData, path.offset());
        DataHolder dataHolder = new DataHolder(buffer, fd, q);
        requests.put(userData, dataHolder);
        q.submit();
        return dataHolder;
    }


    static class DataHolder {
        private StampedLock sl = new StampedLock();
        private final long stamp;
        MemorySegment buffer;
        int fd;
        QuickReader q;

        public DataHolder(MemorySegment buffer, int fd, QuickReader q) {
            stamp = sl.writeLock();
            this.buffer = buffer;
            this.fd = fd;
            this.q = q;
        }


        public void dataIsSet() {
            q.closeFile(fd);
            sl.unlockWrite(stamp);
        }

        public MemorySegment getData() {
            long l = sl.readLock();
            try {
                return buffer;
            } finally {
                sl.unlockRead(l);
            }
        }

        public void freeBuffer() {
            q.free(buffer);
        }

    }

}
