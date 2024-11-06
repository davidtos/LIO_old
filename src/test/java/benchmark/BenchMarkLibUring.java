package benchmark;


import com.davidvlijmincx.RandomReader.BenchmarkFiles;
import com.davidvlijmincx.RandomReader.FdGetter;
import com.davidvlijmincx.RandomReader.IoUringReadMultipleFiles;
import com.davidvlijmincx.generated.io.uring.io_uring_cqe;
import com.davidvlijmincx.generated.io.uring.liburingtest;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.davidvlijmincx.RandomReader.Main.READ_SIZE;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.openjdk.jmh.annotations.Threads.MAX;


public class BenchMarkLibUring {
    static int NR_FILES = BenchmarkFiles.benchmarkFilesAsString.length;
    static Random random = new Random(315315153152442L);
    public static final int NR_OF_FILES = 2211;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark.BenchMarkLibUring.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();

        new Runner(opt).run();
    }

    // @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void readUsingFileChannel(Blackhole blackhole, ExecutionPlanFileChannel plan) throws Throwable {
        final ByteBuffer data = ByteBuffer.allocate(NR_OF_FILES);
        var paths = BenchmarkFiles.benchmarkFiles;
        for (int i = 0; i < paths.length; i++) {
            final int fileIndex = plan.random.nextInt(0, plan.files.length);
            final Path path = plan.files[fileIndex];
            final int fileSize = (int) Files.size(path);
            final int offset = plan.random.nextInt(0, fileSize - READ_SIZE);
            FileChannel fc = plan.fileChannels[fileIndex];
            data.rewind();
            fc.read(data, offset);
            blackhole.consume(data);
        }
    }


    // @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void readUsingFileChannelWithChannelSetup(Blackhole blackhole, ExecutionPlanFileChannel plan) throws Throwable {

        Random random = new Random(315315153152442L);
        Path[] files = BenchmarkFiles.benchmarkFiles;
        FileChannel[] fileChannels = new FileChannel[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                fileChannels[i] = FileChannel.open(files[i], StandardOpenOption.READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        final ByteBuffer data = ByteBuffer.allocate(NR_OF_FILES);

        for (int i = 0; i < files.length; i++) {
            final int fileIndex = random.nextInt(0, plan.files.length);
            final Path path = files[fileIndex];
            final int fileSize = (int) Files.size(path);
            final int offset = random.nextInt(0, fileSize - READ_SIZE);
            FileChannel fc = fileChannels[fileIndex];
            data.rewind();
            fc.read(data, offset);
            blackhole.consume(data);
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
    public void readUsingFileChannelSomeWhatEqualToLibUringSetup(Blackhole blackhole, ExecutionPlanFileChannel plan) throws Throwable {

        int nrThreads = Runtime.getRuntime().availableProcessors();
        List<Path[]> result = splitArrayInChunks(BenchmarkFiles.benchmarkFiles, Runtime.getRuntime().availableProcessors() / nrThreads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < nrThreads; t++) {
                int finalT = t;
                executor.submit(() -> {
                    final ByteBuffer data = ByteBuffer.allocate(READ_SIZE);
                    Path[] paths = result.get(finalT);

                    for (int i = 0; i < paths.length; i++) {
                        final Path path = paths[i];
                        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
                            final int fileSize = (int) Files.size(path);
                            final int offset = plan.random.nextInt(0, fileSize - READ_SIZE);
                            data.rewind();
                            fc.read(data, offset);
                            blackhole.consume(data);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    }
                });
            }
        }

    }


    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    public void ReadUsingLiburingWithFileDescriptorWrapperAndQueuePrepped(ExecutionPlanURING plan, Blackhole blackhole) throws Throwable {

        int nrThreads = plan.nr_t;
        List<String[]> result = splitArrayInChunks(BenchmarkFiles.benchmarkFilesAsString, Runtime.getRuntime().availableProcessors() / nrThreads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < nrThreads; i++) {
                int I = i;
                executor.submit(() -> new benchmark.BenchMarkLibUring.ReadFile(result.get(I), plan.arena[I], plan.uring[I], plan, blackhole, plan.openFiles[I], plan.queue_prepped_offset[I], plan.close_fds[I]));
            }
        }

    }


    class ReadFile implements Runnable {

        String[] paths;
        Arena arena;
        IoUringReadMultipleFiles uring;
        ExecutionPlanURING plan;
        Blackhole blackhole;
        MethodHandle openFiles;
        MethodHandle queue_prepped_offset;
        MethodHandle close_fds;

        public ReadFile(String[] paths, Arena arena, IoUringReadMultipleFiles uring, ExecutionPlanURING plan, Blackhole blackhole,
                        MethodHandle openFiles,
                        MethodHandle queue_prepped_offset,
                        MethodHandle close_fds) {
            this.paths = paths;
            this.arena = arena;
            this.uring = uring;
            this.plan = plan;
            this.blackhole = blackhole;
            this.openFiles = openFiles;
            this.queue_prepped_offset = queue_prepped_offset;
            this.close_fds = close_fds;
        }

        @Override
        public void run() {

            Map<Integer, benchmark.BenchMarkLibUring.FileAtt2> attMap = new HashMap<>();

            //// Setting up FDs

            String[] paths = BenchmarkFiles.benchmarkFilesAsString;
            MemorySegment pathsArray = uring.arena.allocate(ValueLayout.ADDRESS, paths.length);

            MemorySegment fdPointer = null;
            try {
                fdPointer = FdGetter.openFiles(paths, uring.arena, openFiles, pathsArray);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            uring.passFdDirectly(paths.length, fdPointer);

            /// Setup reads

            final benchmark.BenchMarkLibUring.FileAtt2[] atts = new benchmark.BenchMarkLibUring.FileAtt2[paths.length];

            for (int i = 0; i < paths.length; i++) {
                final String current_path = paths[i];
                final int fileSize;
                try {
                    fileSize = (int) Files.size(Path.of(current_path));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                final int offset = random.nextInt(0, fileSize - READ_SIZE);

                atts[i] = new benchmark.BenchMarkLibUring.FileAtt2(current_path, i, offset, READ_SIZE, arena);
                try {
                    uring.createReadRequestDirectlyWithOffset(queue_prepped_offset, i, atts[i].buffer, READ_SIZE, atts[i].user_data, offset);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                benchmark.BenchMarkLibUring.FileAtt2 att = atts[i];
                attMap.put(i, att);

                if (i % 5 == 0) {
                    uring.submit();
                }

            }

            uring.submit();

            for (int i = 0; i < paths.length; i++) {

                var pntr = uring.see(uring.arena.allocate(io_uring_cqe.layout()));
                int userData = liburingtest.io_uring_cqe_get_data(pntr).get(JAVA_INT, 0);

                // blackhole.consume(java.nio.charset.StandardCharsets.UTF_8.decode(attMap.get(userData).buffer.asByteBuffer()));
                blackhole.consume(attMap.get(userData).buffer.asByteBuffer().array());

                uring.seen(pntr);

            }

            try {
                close_fds.invoke(fdPointer, paths.length);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }


    public static <T> List<T[]> splitArrayInChunks(T[] array, int n) {
        List<T[]> chunks = new ArrayList<>();
        int chunkSize = (int) Math.ceil((double) array.length / n);

        for (int i = 0; i < array.length; i += chunkSize) {
            T[] chunk = Arrays.copyOfRange(array, i, Math.min(array.length, i + chunkSize));
            chunks.add(chunk);
        }

        return chunks;
    }


    public static class FileAtt2 {
        public int user_data;
        public MemorySegment buffer;
        public String path;
        public int offset;
        public int length;

        public FileAtt2(String path, int userData, int offset, int length, Arena arena) {
            this.user_data = userData;
            this.path = path;
            this.offset = offset;
            this.length = length;
            this.buffer = arena.allocate(JAVA_BYTE, READ_SIZE);
        }
    }


}

