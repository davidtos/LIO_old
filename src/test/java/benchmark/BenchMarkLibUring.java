package benchmark;


import com.davidvlijmincx.newreader.Holder;
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
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.openjdk.jmh.annotations.Threads.MAX;


public class BenchMarkLibUring {
    static int NR_FILES = BenchmarkFiles.filesTooRead.length;
    public static final int NR_OF_FILES = 2211;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark.BenchMarkLibUring.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();

        new Runner(opt).run();
    }


  //  @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void _0_readUsingFileChannel(Blackhole blackhole) throws Throwable {

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


    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(NR_OF_FILES)
    @Threads(MAX)
    public void _1_libUring(Blackhole blackhole, ExecutionPlanSmallUring plan) {

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

                blackhole.consume(StandardCharsets.US_ASCII.decode(holder.buffer().asByteBuffer()));

                q.free(holder.buffer());
                q.closeFile(holder.fd());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}

