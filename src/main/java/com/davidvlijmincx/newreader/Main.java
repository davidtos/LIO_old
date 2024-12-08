package com.davidvlijmincx.newreader;

import com.davidvlijmincx.setup.BenchmarkFiles;
import com.davidvlijmincx.setup.FileTooReadData;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class Main {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        FileTooReadData[] filesTooRead = BenchmarkFiles.filesTooRead;

        var q = new QuickReader(filesTooRead.length, true);

//        main.readUsingFileChannelWithChannelSetup(filesTooRead);
        main.liburin(q, filesTooRead);

    }

    public void liburin(QuickReader q, FileTooReadData... paths) {

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
                   // System.out.println("userData = " + userData);

           //     System.out.println(StandardCharsets.US_ASCII.decode(holder.buffer().asByteBuffer()));
//
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
            final ByteBuffer data = ByteBuffer.allocate(files[i].bufferSize());
            FileChannel fc = fileChannels[i];
            fc.read(data, files[i].offset());
            data.flip();
           // final CharBuffer decode = US_ASCII.decode(data);
           // System.out.println(decode);

        }

        for (FileChannel fc : fileChannels) {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
