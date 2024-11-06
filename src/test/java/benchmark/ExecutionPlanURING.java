package benchmark;


import com.davidvlijmincx.RandomReader.IoUringReadMultipleFiles;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static benchmark.BenchMarkLibUring.NR_FILES;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@State(Scope.Benchmark)
public class ExecutionPlanURING {

    int nr_t = Runtime.getRuntime().availableProcessors()/4;
    IoUringReadMultipleFiles[] uring = new IoUringReadMultipleFiles[nr_t];
    Arena[] arena = new Arena[nr_t];
    MethodHandle[] openFiles = new MethodHandle[nr_t];
    MethodHandle[] queue_prepped_offset = new MethodHandle[nr_t];
    MethodHandle[] close_fds = new MethodHandle[nr_t];


    @Setup
    public void setup() {


        for (int i = 0; i < nr_t; i++) {
            this.uring[i] = new IoUringReadMultipleFiles(NR_FILES);
            this.arena[i] = Arena.ofConfined();


            SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", arena[i]);


            this.openFiles[i] = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("open_files").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            this.queue_prepped_offset[i] = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("queue_prepped_offset").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, JAVA_INT)
            );

            this.close_fds[i] = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("close_fds").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
        }


    }


    @TearDown
    public void tearDown() {
        for (int i = 0; i < nr_t; i++) {
            uring[i].close();
            arena[i].close();
        }
    }



}
