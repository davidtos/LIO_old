package com.davidvlijmincx.newreader;



import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;


public class MultiTester {

    static Map<Integer, DataHolder> requests = new ConcurrentHashMap<>();

    interface MasterRecord{}
    record ReadRequest(String path, int offset, int bufferSize) implements MasterRecord {}
    record WriteRequest(String path, byte[] content, int offset) implements MasterRecord {}


    public static void main(String[] args) throws InterruptedException {
        MultiTester main = new MultiTester();

        String myContent = "12345678";

        MasterRecord[] filesTooRead = new MasterRecord[]{
                new ReadRequest("./tmp_file_read",  10, 10),
                new WriteRequest("./tmp_file",  myContent.getBytes(), 1),
                new ReadRequest("./tmp_file_read",  10, 10),
                new WriteRequest("./tmp_file",  myContent.getBytes(), 2),
                new ReadRequest("./tmp_file_read",  10, 10),
                new WriteRequest("./tmp_file",  myContent.getBytes(), 3),
                new ReadRequest("./tmp_file_read",  10, 10)
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
                    requests.remove(userData);

                }
            });

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < filesTooRead.length; i++) {
                    if(filesTooRead[i] instanceof ReadRequest s){
                        ReadRequestHolder dataHolder = main.submitRead(q, s, i);
                        executor.execute(() -> {
                            System.out.println(UTF_8.decode(dataHolder.getData().asByteBuffer()));
                            dataHolder.freeBuffer();
                        });
                    } else if(filesTooRead[i] instanceof WriteRequest s){
                        WriteRequestHolder dataHolder = main.prepareWrite(q, s.path, s.content, i, s.offset);
                        executor.execute(dataHolder::waitForWriteToFinish);
                    }

                }
                q.submit();
            }


            thread.join();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    ReadRequestHolder submitRead(QuickRDWR q, ReadRequest path, int userData) {
        final int fd = q.open(path.path,0,0);
        final MemorySegment buffer = q.prepareReadRequest(fd, path.bufferSize(), userData, path.offset());
        ReadRequestHolder dataHolder = new ReadRequestHolder(buffer, fd, q);
        requests.put(userData, dataHolder);
        return dataHolder;
    }

    WriteRequestHolder prepareWrite(QuickRDWR q, String path, byte[] content, int userData, int offset){
        int fd = q.open(path, 0, 2);
        MemorySegment writeSegment = q.malloc(content.length);
        MemorySegment.copy(content, 0, writeSegment, JAVA_BYTE, 0, content.length);
        q.prepareWriteRequest(userData, fd, writeSegment, offset);
        WriteRequestHolder dataHolder = new WriteRequestHolder(writeSegment, fd, q);
        requests.put(userData, dataHolder);
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

        public void freeBuffer() {
            q.free(buffer);
        }

    }

    static class ReadRequestHolder extends DataHolder{

        public ReadRequestHolder(MemorySegment buffer, int fd, QuickRDWR q) {
            super(buffer, fd, q);
        }

        public MemorySegment getData() {
            try {
                lock.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return buffer;
        }

    }

    static class WriteRequestHolder extends DataHolder{

        public WriteRequestHolder(MemorySegment buffer, int fd, QuickRDWR q) {
            super(buffer, fd, q);
        }

        public void dataIsSet() {
            super.dataIsSet();
            freeBuffer();
        }

        public void waitForWriteToFinish(){
            try {
                lock.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
