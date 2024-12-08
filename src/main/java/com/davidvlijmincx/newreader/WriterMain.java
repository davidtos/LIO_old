package com.davidvlijmincx.newreader;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class WriterMain {


    public static void main(String[] args) {
        var q = new QuickWriter(5, true);

//        int flags = 0x0201 | 0x02000 | 0x0040; // O_WRONLY | O_TRUNC | O_CREAT
        int flags = 2;
        int mode = 0644;
        String path = "./tmp_file";
        String content = "Hello, io_uring!!!! from the simple api";

        int fd = q.open(path, mode, flags);

        MemorySegment segment = q.malloc(content.length());

        var StringBytes = content.getBytes();
        MemorySegment.copy(StringBytes, 0, segment, JAVA_BYTE, 0, StringBytes.length);

        q.submitWriteRequest(1,fd,segment,0);

        q.submit();

        int i = q.waitAndSee();
        System.out.println("userdata = " + i);

        q.free(segment);
        q.closeFile(fd);


    }
}
