package benchmark;


import com.davidvlijmincx.generated.io.uring.liburingtest;
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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
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


    @Benchmark()
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
            FileChannel fc = fileChannels[i];
            fc.read(data, files[i].offset());
            String fileContent = new String(data.array(), StandardCharsets.UTF_8);
            blackhole.consume(fileContent);

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
    public void libUring(Blackhole blackhole, ExecutionPlanSmallUring plan) {

        var q = plan.q;
        var paths = BenchmarkFiles.filesTooRead;
        HashMap<Integer, Holder> fds = new HashMap<>();

        try {
            for (int i = 0; i < paths.length; i++) {
                MemorySegment fd = q.openFile(paths[i].sPath());

                MemorySegment buffer =  q.malloc(paths[i].bufferSize());
                // MemorySegment buffer =  arena.allocate(paths[i].bufferSize());
                q.submitReadRequest(fd, buffer, buffer.byteSize(), i, paths[i].offset());
                fds.put(i, new Holder(fd, buffer));

                if(i % 100 == 0){
                    q.submit();
                }

            }

            q.submit();

            for (int i = 0; i < paths.length; i++) {

                MemorySegment pntr = q.readFromCompletion();
                int userData = liburingtest.io_uring_cqe_get_data(pntr).get(JAVA_INT, 0);
                Holder holder = fds.get(userData);

                String a = new String(holder.buffer().toArray(JAVA_BYTE), StandardCharsets.UTF_8);
                blackhole.consume(a);

                q.seen(pntr);
                q.free(holder.buffer());
                q.closeFile(holder.fd());

//                int userData = q.waitAndSee();
//                Holder holder = fds.get(userData);
//                q.free(holder.buffer());
//                q.closeFile(holder.fd());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}

