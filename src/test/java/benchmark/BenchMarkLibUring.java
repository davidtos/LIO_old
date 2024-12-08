package benchmark;


import com.davidvlijmincx.newreader.Holder;
import com.davidvlijmincx.newreader.QuickReader;
import com.davidvlijmincx.setup.BenchmarkFiles;
import com.davidvlijmincx.setup.FileTooReadData;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

import static org.openjdk.jmh.annotations.Threads.MAX;


public class BenchMarkLibUring {
    static int NR_FILES = BenchmarkFiles.filesTooRead.length;
    public static final int NR_OF_FILES = 2300;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark.BenchMarkLibUring.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();

        new Runner(opt).run();
    }


 //   @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void readUsingFileChannel(Blackhole blackhole) throws Throwable {

        FileTooReadData[] files = BenchmarkFiles.filesTooRead;

        FileChannel[] fileChannels = new FileChannel[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                fileChannels[i] = FileChannel.open(files[i].path(), StandardOpenOption.READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < files.length; i++) {
            final ByteBuffer data = ByteBuffer.allocate(files[i].bufferSize());
            final FileChannel fc = fileChannels[i];
            fc.read(data, files[i].offset());
            data.flip();
            final CharBuffer decode = StandardCharsets.US_ASCII.decode(data);
            blackhole.consume(decode);

        }

        for (FileChannel fc : fileChannels) {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


 //   @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void libUring(Blackhole blackhole, ExecutionPlanSmallUring plan) {

        final var q = plan.q;
        final var paths = BenchmarkFiles.filesTooRead;
        final HashMap<Integer, Holder> fds = new HashMap<>();

        try {
            for (int i = 0; i < paths.length; i++) {
                final int fd = q.open(paths[i].sPath());

                final MemorySegment buffer = q.submitReadRequest2(fd, paths[i].bufferSize(), i, paths[i].offset());
                fds.put(i, new Holder(fd, buffer));

                if (i % 100 == 0) {
                    q.submit();
                }

            }

            q.submit();

            for (int i = 0; i < paths.length; i++) {
                final int userData = q.waitAndSee();
                final Holder holder = fds.get(userData);
                //    System.out.println("userData = " + userData);

              //  blackhole.consume(StandardCharsets.US_ASCII.decode(holder.buffer().asByteBuffer()));

                q.free(holder.buffer());
                q.closeFile(holder.fd());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void libUringVirtualThreads(Blackhole blackhole, ExecutionPlanSmallUring plan) throws InterruptedException {

        final Map<Integer, DataHolder> requests = new ConcurrentHashMap<>();
        final var q = plan.q;
        final var filesTooRead = BenchmarkFiles.filesTooRead;

        Thread thread = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < filesTooRead.length; i++) {
                //    System.out.println("i = " + i);
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
                requests.put(i, new DataHolder(null, 1, null));
                DataHolder dataHolder = submitRead(q, filesTooRead[i], i, requests);
                executor.execute(() -> {
                    blackhole.consume(StandardCharsets.US_ASCII.decode(dataHolder.getData().asByteBuffer()));
                    dataHolder.freeBuffer();
                });
            }
        }

        thread.join();

    }

    DataHolder submitRead(QuickReader q, FileTooReadData path, int userData, Map<Integer, DataHolder> requests) {
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

