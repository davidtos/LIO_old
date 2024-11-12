package com.davidvlijmincx.RandomReader;

import com.davidvlijmincx.generated.io.uring.io_uring_cqe;
import com.davidvlijmincx.generated.io.uring.liburingtest;
import com.davidvlijmincx.setup.BenchmarkFiles;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;


import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class MultiMain {

    public static final int READ_SIZE = 1024 * 4;
    static Random random = new Random(315315153152442L);

    public static void main(String[] args) {

        for (int o = 0; o < 1; o++) {
            int nrThreads = 32;
            List<String[]> result = splitArrayInChunks(BenchmarkFiles.benchmarkFilesAsString, nrThreads);


            try (var arena = Arena.ofShared()) {
                SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", arena);
                MethodHandle openFiles = Linker.nativeLinker().downcallHandle(
                        SYMBOL_LOOKUP.find("open_files").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );

                MethodHandle queue_prepped_offset = Linker.nativeLinker().downcallHandle(
                        SYMBOL_LOOKUP.find("queue_prepped_offset").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, JAVA_INT)
                );

                MethodHandle close_fds = Linker.nativeLinker().downcallHandle(
                        SYMBOL_LOOKUP.find("close_fds").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                );

                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (int i = 0; i < nrThreads; i++) {
                        executor.execute(new ReadFile(result.get(i), openFiles, queue_prepped_offset, close_fds));
                    }
                }
            }
        }

    }


    static class ReadFile implements Runnable {


        String[] paths;
        MethodHandle openFiles;
        MethodHandle queue_prepped_offset;
        MethodHandle close_fds;

        public ReadFile(String[] paths, MethodHandle openFiles, MethodHandle queue_prepped_offset, MethodHandle close_fds) {
            this.paths = paths;
            this.openFiles = openFiles;
            this.queue_prepped_offset = queue_prepped_offset;
            this.close_fds = close_fds;
        }

        @Override
        public void run() {

            int NR_FILES = paths.length;
            try (var arena = Arena.ofConfined(); IoUringReadMultipleFiles uring = new IoUringReadMultipleFiles(NR_FILES)) {

                Map<Integer, Main.FileAtt2> attMap = new HashMap<>();

                //// Setting up FDs
                MemorySegment pathsArray = uring.arena.allocate(ValueLayout.ADDRESS, NR_FILES);

                MemorySegment fdPointer = FdGetter.openFiles(paths, uring.arena, openFiles, pathsArray);
                uring.passFdDirectly(NR_FILES, fdPointer);


                /// Setup reads
                final Main.FileAtt2[] atts = new Main.FileAtt2[NR_FILES];

                for (int i = 0; i < NR_FILES; i++) {
                    final String current_path = paths[i];
                    final int fileSize = (int) Files.size(Path.of(current_path));
                    final int offset = random.nextInt(0, fileSize - READ_SIZE);

                    atts[i] = new Main.FileAtt2(current_path, i, offset, READ_SIZE, arena);
                    uring.createReadRequestDirectlyWithOffset(queue_prepped_offset, i, atts[i].buffer, READ_SIZE, atts[i].user_data, offset);
                    Main.FileAtt2 att = atts[i];
                    attMap.put(i, att);

                    uring.submit();

                }


                for (int i = 0; i < NR_FILES; i++) {
                    var pntr = uring.see(uring.arena.allocate(io_uring_cqe.layout()));
                    int userData = liburingtest.io_uring_cqe_get_data(pntr).get(JAVA_INT, 0);

                  //  System.out.println(attMap.get(userData).path + ": " + java.nio.charset.StandardCharsets.UTF_8.decode(attMap.get(userData).buffer.asByteBuffer()).toString().substring(0, 5));
                    String path = attMap.get(userData).path;
                    java.nio.charset.StandardCharsets.UTF_8.decode(attMap.get(userData).buffer.asByteBuffer());

                    uring.seen(pntr);

                }

                close_fds.invoke(fdPointer, NR_FILES);


            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

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

    public static <T> List<T[]> splitArrayInChunks(T[] array, int n) {
        List<T[]> chunks = new ArrayList<>();
        int chunkSize = (int) Math.ceil((double) array.length / n);

        for (int i = 0; i < array.length; i += chunkSize) {
            T[] chunk = Arrays.copyOfRange(array, i, Math.min(array.length, i + chunkSize));
            chunks.add(chunk);
        }

        return chunks;
    }


}
