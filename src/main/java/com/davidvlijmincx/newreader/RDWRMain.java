package com.davidvlijmincx.newreader;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class RDWRMain {


    public static void main(String[] args) {
        String writePath = "./tmp_file";
        String readPath = "./tmp_file_read";
        String content = "Hello, io_uring! from uni reader!";

        QuickRDWR q = new QuickRDWR(4, true);

        // gonna do a read
        int fd = q.open(readPath, 0, 0);
                                // fd -> path, userdata -> random gui?
        MemorySegment segment = q.prepareReadRequest(fd, 10, 1, 0);

        q.submit();

        int userData = q.waitAndSee();

        System.out.println("userData = " + userData);

        System.out.println(US_ASCII.decode(segment.asByteBuffer()));
        q.free(segment);
        q.closeFile(fd);

        // gonna do a write

        int wfd = q.open(writePath, 0, 2);

        MemorySegment writeSegment = q.malloc(content.length());

        var StringBytes = content.getBytes();
        MemorySegment.copy(StringBytes, 0, writeSegment, JAVA_BYTE, 0, StringBytes.length);

        q.prepareWriteRequest(1,wfd,writeSegment,0);

        q.submit();

        int i = q.waitAndSee();
        System.out.println("userdata = " + i);

        q.free(writeSegment);
        q.closeFile(wfd);

        q.close();
    }



}
