package com.davidvlijmincx.RandomReader;

import com.davidvlijmincx.generated.io.uring.io_uring_cqe;
import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Main {

    static int NR_FILES = BenchmarkFiles.benchmarkFilesAsString.length;
    static Random random = new Random(315315153152442L);
    public static final int READ_SIZE = 1024 * 4;

    public static void main(String[] args) {

        try (var arena = Arena.ofConfined(); IoUringReadMultipleFiles uring = new IoUringReadMultipleFiles(NR_FILES)) {

            SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", arena);
            MethodHandle openFiles = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("open_files").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            MethodHandle queue_prepped_offset = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("queue_prepped_offset").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, JAVA_INT)
            );

            MethodHandle close_fds = Linker.nativeLinker().downcallHandle(
                    SYMBOL_LOOKUP.find("close_fds").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            Map<Integer, FileAtt2> attMap = new HashMap<>();

            //// Setting up FDs

            String[] paths = BenchmarkFiles.benchmarkFilesAsString;
            MemorySegment pathsArray = uring.arena.allocate(ValueLayout.ADDRESS, NR_FILES);

            MemorySegment fdPointer = FdGetter.openFiles(paths, uring.arena, openFiles, pathsArray);
            uring.passFdDirectly(NR_FILES, fdPointer);


            /// Setup reads
            final FileAtt2[] atts = new FileAtt2[NR_FILES];

            for (int i = 0; i < NR_FILES; i++) {
                final String current_path = paths[i];
                final int fileSize = (int) Files.size(Path.of(current_path));
                final int offset = random.nextInt(0, fileSize - READ_SIZE);

                atts[i] = new FileAtt2(current_path, i, offset, READ_SIZE, arena);
                uring.createReadRequestDirectlyWithOffset(queue_prepped_offset, i, atts[i].buffer, READ_SIZE, atts[i].user_data, offset);
                FileAtt2 att = atts[i];
                attMap.put(i, att);

            }

            uring.submit();

            for (int i = 0; i < NR_FILES; i++) {
                var pntr = uring.see(uring.arena.allocate(io_uring_cqe.layout()));
                int userData = liburingtest.io_uring_cqe_get_data(pntr).get(JAVA_INT, 0);

               // System.out.println(attMap.get(userData).path + ": " + java.nio.charset.StandardCharsets.UTF_8.decode(attMap.get(userData).buffer.asByteBuffer()));
                String path = attMap.get(userData).path;
                java.nio.charset.StandardCharsets.UTF_8.decode(attMap.get(userData).buffer.asByteBuffer());

                uring.seen(pntr);

            }

            close_fds.invoke(fdPointer,NR_FILES);


        } catch (Throwable e) {
            throw new RuntimeException(e);
        }


    }



    public static class FileAtt2{
        public int user_data;
        public MemorySegment buffer;
        public String path;
        public int offset;
        public int length;

        public FileAtt2(String path, int userData, int offset, int length, Arena arena){
            this.user_data = userData;
            this.path = path;
            this.offset = offset;
            this.length = length;
            this.buffer = arena.allocate(JAVA_BYTE,  READ_SIZE);
        }
    }

}

