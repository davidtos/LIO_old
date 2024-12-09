package com.davidvlijmincx.newreader;

import com.davidvlijmincx.setup.FileTooReadData;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;


public class VtTester {

    static Map<Integer, DataHolder> requests = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {

        VtTester main = new VtTester();
        //   FileTooReadData[] filesTooRead = BenchmarkFiles.filesTooRead;

        FileTooReadData[] filesTooRead = new FileTooReadData[]{
                new FileTooReadData("./tmp_file_read", null, 10, 10, 10),
                new FileTooReadData("./tmp_file_read", null, 10, 10, 10),
                new FileTooReadData("./tmp_file_read", null, 10, 10, 10),
                new FileTooReadData("./tmp_file_read", null, 10, 10, 10),
        };

        try (var q = new QuickRDWR(filesTooRead.length, true)) {

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
                        System.out.println(UTF_8.decode(dataHolder.getData().asByteBuffer()));
                        dataHolder.freeBuffer();
                    });
                }
            }

            thread.join();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    DataHolder submitRead(QuickRDWR q, FileTooReadData path, int userData) {
        final int fd = q.open(path.sPath(),0,0);
        final MemorySegment buffer = q.prepareReadRequest(fd, path.bufferSize(), userData, path.offset());
        DataHolder dataHolder = new DataHolder(buffer, fd, q);
        requests.put(userData, dataHolder);
        q.submit();
        return dataHolder;
    }

    DataHolder prepareWrite(QuickRDWR q, String path, byte[] content, int userData, int offset){
        int fd = q.open(path, 0, 2);
        MemorySegment writeSegment = q.malloc(content.length);
        MemorySegment.copy(content, 0, writeSegment, JAVA_BYTE, 0, content.length);
        q.prepareWriteRequest(userData, fd, writeSegment, offset);
        DataHolder dataHolder = new DataHolder(writeSegment, fd, q);
        requests.put(userData, dataHolder);
        q.submit();
        return dataHolder;
    }


    static class DataHolder {
        CompletableFuture<Void> lock = new CompletableFuture<>();
        MemorySegment buffer;
        int fd;
        QuickRDWR q;

        public DataHolder(MemorySegment buffer, int fd, QuickRDWR q) {
            this.buffer = buffer;
            this.fd = fd;
            this.q = q;
        }


        public void dataIsSet() {
            q.closeFile(fd);
            lock.complete(null);
        }

        public MemorySegment getData() {
            try {
                lock.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return buffer;
        }

        public void freeBuffer() {
            q.free(buffer);
        }

    }

}
