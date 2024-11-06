package benchmark;


import com.davidvlijmincx.RandomReader.BenchmarkFiles;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

@State(Scope.Thread)
public class ExecutionPlanFileChannel {

    Random random = new Random(315315153152442L);
    Path[] files;
    FileChannel[] fileChannels;

    @Setup
    public void setup() {
        files = BenchmarkFiles.benchmarkFiles;
        fileChannels = new FileChannel[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                fileChannels[i] = FileChannel.open(files[i], StandardOpenOption.READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @TearDown
    public void tearDown() {
        for (FileChannel fc: fileChannels) {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
