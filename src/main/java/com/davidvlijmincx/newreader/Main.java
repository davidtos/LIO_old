package com.davidvlijmincx.newreader;

import com.davidvlijmincx.setup.BenchmarkFiles;
import com.davidvlijmincx.setup.FileTooReadData;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class Main {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        FileTooReadData[] filesTooRead = BenchmarkFiles.filesTooRead;

        var q = new QuickReader(filesTooRead.length, true);

        main.readUsingFileChannelWithChannelSetup(filesTooRead);

    //    main.liburin(q, filesTooRead);

    }

    public void liburin(QuickReader q, FileTooReadData... paths) {

        HashMap<Integer, Holder> fds = new HashMap<>();

        try {
            for (int i = 0; i < paths.length; i++) {
                int fd = q.open(paths[i].sPath());

                MemorySegment buffer = q.submitReadRequest2(fd, paths[i].bufferSize(), i, paths[i].offset());
                fds.put(i, new Holder(fd, buffer));

                if (i % 100 == 0) {
                    q.submit();
                }

            }

            q.submit();

            for (int i = 0; i < paths.length; i++) {
                int userData = q.waitAndSee();
                Holder holder = fds.get(userData);
                //    System.out.println("userData = " + userData);

                holder.buffer().set(ValueLayout.JAVA_BYTE,4095,(byte)0);
               System.out.println(holder.buffer().getString(0,US_ASCII));
             //   holder.buffer().getString()

                q.free(holder.buffer());
                q.closeFile(holder.fd());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void readUsingFileChannelWithChannelSetup(FileTooReadData... files) throws Exception {

        FileChannel[] fileChannels = new FileChannel[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                fileChannels[i] = FileChannel.open(files[i].path(), StandardOpenOption.READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < files.length; i++) {
            final ByteBuffer data = ByteBuffer.allocateDirect(files[i].bufferSize());
            FileChannel fc = fileChannels[i];
            fc.read(data, files[i].offset());

            CharBuffer decode = StandardCharsets.US_ASCII.decode(data);
            System.out.println(decode);

        }

        for (FileChannel fc : fileChannels) {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


//    public static <T> List<T[]> splitArrayInChunks(T[] array, int n) {
//        List<T[]> chunks = new ArrayList<>();
//        int chunkSize = (int) Math.ceil((double) array.length / n);
//
//        for (int i = 0; i < array.length; i += chunkSize) {
//            T[] chunk = Arrays.copyOfRange(array, i, Math.min(array.length, i + chunkSize));
//            chunks.add(chunk);
//        }
//
//        return chunks;
//    }


//public void readFiles(SymbolLookup SYMBOL_LOOKUP, String... paths) throws Exception {
//
//    try (var q = new QuickReader(paths.length, true, SYMBOL_LOOKUP); var arena = Arena.ofConfined()) {
//        for (int i = 0; i < paths.length; i++) {
//            MemorySegment fd = q.openFile(paths[i]);
//
//            MemorySegment buffer =  q.malloc(4); // arena.allocate(4);
//            q.submitReadRequest(fd, buffer, buffer.byteSize(), i, 0);
//
//            q.submit();
//
////            MemorySegment pntr = q.readFromCompletion();
////            int userData = liburingtest.io_uring_cqe_get_data(pntr).get(JAVA_INT, 0);
//            int userData = q.waitAndSee();
//            System.out.println("userData = " + userData);
//            System.out.println(java.nio.charset.StandardCharsets.UTF_8.decode(buffer.asByteBuffer()));
//
//            q.free(buffer);
//          //  q.seen(pntr);
//            q.closeFile(fd);
//        }
//    }
//}


}
