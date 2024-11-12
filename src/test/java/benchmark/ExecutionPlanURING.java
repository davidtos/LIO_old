package benchmark;


import com.davidvlijmincx.RandomReader.FdGetter;
import com.davidvlijmincx.RandomReader.IoUringReadMultipleFiles;
import com.davidvlijmincx.setup.BenchmarkFiles;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static benchmark.BenchMarkLibUring.NR_FILES;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@State(Scope.Thread)
public class ExecutionPlanURING {

    int nr_t = 8;
    IoUringReadMultipleFiles[] uring = new IoUringReadMultipleFiles[nr_t];
    Arena[] arena = new Arena[nr_t];
    MethodHandle openFiles;
    MethodHandle queue_prepped_offset;
    MethodHandle close_fds;
    MemorySegment[] fdPointer = new MemorySegment[nr_t];
    String[][] resultlist = new String[nr_t][];


    @Setup
    public void setup() throws Throwable {
        var arena = Arena.global();
        SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", arena);
        openFiles = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("open_files").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        queue_prepped_offset = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("queue_prepped_offset").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, JAVA_INT)
        );

        close_fds = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("close_fds").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        for (int i = 0; i < nr_t; i++) {
            this.uring[i] = new IoUringReadMultipleFiles(NR_FILES);
            this.arena[i] = Arena.ofConfined();

        }


        List<String[]> result = splitArrayInChunks(BenchmarkFiles.benchmarkFilesAsString, nr_t);

        for (int i = 0; i < nr_t; i++) {
            MemorySegment pathsArray = uring[i].arena.allocate(ValueLayout.ADDRESS, NR_FILES);
            resultlist[i] = result.get(i);
            fdPointer[i] = FdGetter.openFiles(result.get(i), uring[i].arena, openFiles, pathsArray);
            uring[i].passFdDirectly(NR_FILES, fdPointer[i]);
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


    @TearDown
    public void tearDown() throws Throwable {
        for (int i = 0; i < nr_t; i++) {
            uring[i].close();
            arena[i].close();
            close_fds.invokeExact(fdPointer[i], resultlist[i].length);
        }
    }


}
