package com.davidvlijmincx;


import com.davidvlijmincx.setup.BenchmarkFiles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static com.davidvlijmincx.RandomReader.Main.READ_SIZE;

public class ChannelMain {

    public static void main(String[] args) throws IOException {
        Random random = new Random(315315153152442L);
        Path[] files = BenchmarkFiles.benchmarkFiles;

        for (Path file : files) {
            try (var fc = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer data = ByteBuffer.allocate(1);
                Path path = file;
                int fileSize = (int) Files.size(path);
                int offset = random.nextInt(0, fileSize - 1);
                data.rewind();
                fc.read(data, offset);
                System.out.println(new String(data.array()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
