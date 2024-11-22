package benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BufferTest {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark.BufferTest.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();

        new Runner(opt).run();
    }


    @Benchmark
    public void directByteBufferToString(BufferPlan plan, Blackhole blackhole) {
        blackhole.consume(StandardCharsets.UTF_8.decode(plan.directBuffer));
    }

    @Benchmark
    public void byteBufferToString(BufferPlan plan, Blackhole blackhole) {
        blackhole.consume(StandardCharsets.UTF_8.decode(plan.buffer));
    }

    @Benchmark
    public void memorySegmentToString(BufferPlan plan, Blackhole blackhole) {
        blackhole.consume(StandardCharsets.UTF_8.decode(plan.segment.asByteBuffer()));
    }


}
