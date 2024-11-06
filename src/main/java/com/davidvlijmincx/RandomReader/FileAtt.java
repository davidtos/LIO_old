package com.davidvlijmincx.RandomReader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class FileAtt {

    public int user_data;
    public MemorySegment buffer;
    public String path;
    public int fd;
    public int fileSize;
    public int offset;


    public FileAtt(String path, int fd, int userData, Arena arena, int offset, int bufferSize, int fileSize){
        this.user_data = userData;
        this.path = path;
        this.fd = fd;
        this.fileSize = fileSize;
        this.buffer = arena.allocate(JAVA_BYTE,  bufferSize);
        this.offset = offset;
    }


}
