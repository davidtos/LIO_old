package com.davidvlijmincx.newreader;

import com.davidvlijmincx.generated.io.uring.io_uring;
import com.davidvlijmincx.generated.io.uring.io_uring_params;
import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

public class QuickRDWR implements AutoCloseable{
    
    private final Arena arena;
    private final MemorySegment ring;

    private static final MethodHandle open;
    private static final MethodHandle malloc;
    private static final MethodHandle free;
    private static final MethodHandle seeAndClose;
    private static final MethodHandle close;
    private static final MethodHandle read_with_offset_buffer;

    static {
        SymbolLookup fileManagerLib = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", Arena.global());

        Linker linker = Linker.nativeLinker();

        open = linker.downcallHandle(
                linker.defaultLookup().find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)
        );

        free = linker.downcallHandle(
                linker.defaultLookup().find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        malloc = linker.downcallHandle(
                linker.defaultLookup().find("malloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT)
        );

        close = linker.downcallHandle(
                fileManagerLib.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_INT)
        );

        read_with_offset_buffer = Linker.nativeLinker().downcallHandle(
                fileManagerLib.find("read_with_offset_buffer").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, JAVA_INT)
        );

        seeAndClose = Linker.nativeLinker().downcallHandle(
                fileManagerLib.find("see_and_close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS)
        );
    }

    public QuickRDWR(int QD, boolean polling) {
        this.arena = Arena.ofShared();
        int ret;
        if (polling) {
            MemorySegment ioup = arena.allocate(io_uring_params.layout());
            io_uring_params.flags(ioup, (1 << 1));
            int ThreadIdleTimeInMilliseconds = 2000;
            io_uring_params.sq_thread_idle(ioup, ThreadIdleTimeInMilliseconds);

            ring = arena.allocate(io_uring.layout());
            ret = liburingtest.io_uring_queue_init_params(QD, ring, ioup);
        } else {
            ring = arena.allocate(io_uring.layout());
            ret = liburingtest.io_uring_queue_init(QD, ring, 0);
        }

        if (ret < 0) {
            throw new RuntimeException("ring init ret = " + ret);
        }
    }


    public int open(String path, int mode, int flags) {
        try {
            var StringBytes = path.getBytes();
            MemorySegment memorySegment = mallocWithAutoCleaner(StringBytes.length);
            MemorySegment.copy(StringBytes, 0, memorySegment, JAVA_BYTE, 0, StringBytes.length);
            return (int) open.invokeExact(memorySegment, flags, mode);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void closeFile(int fd) {
        try {
            close.invokeExact(fd);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment malloc(int size) {
        try {
            return ((MemorySegment) malloc.invokeExact(size)).reinterpret(size);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment mallocWithAutoCleaner(int size) {
        try {
            return ((MemorySegment) malloc.invokeExact(size)).reinterpret(size, Arena.ofAuto(), this::free);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void free(MemorySegment mem) {
        try {
            free.invoke(mem);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void prepareWriteRequest(long userData, int fd, MemorySegment bufPtr, int offset) {
        MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
        liburingtest.io_uring_sqe_set_data_long(sqe,  userData);
        liburingtest.io_uring_prep_write(sqe, fd, bufPtr, (int) bufPtr.byteSize(), offset);
    }

    public MemorySegment prepareReadRequest(int fd, long bufferSize, long userDate, int offset) {
        try {
            return ((MemorySegment) read_with_offset_buffer.invokeExact(ring, fd, bufferSize, userDate, offset)).reinterpret(bufferSize);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void submit() {
        liburingtest.io_uring_submit(ring);
    }

    public int waitAndSee() {
        try {
            return (int) seeAndClose.invokeExact(ring);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public void close(){
        liburingtest.io_uring_queue_exit(ring);
        arena.close();
    }
    

}
