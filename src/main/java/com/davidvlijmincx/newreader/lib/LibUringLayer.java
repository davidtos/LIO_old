package com.davidvlijmincx.newreader.lib;

import com.davidvlijmincx.generated.io.uring.io_uring;
import com.davidvlijmincx.generated.io.uring.io_uring_params;
import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

class LibUringLayer implements AutoCloseable {

    private static final Arena AUTO = Arena.ofAuto();
    private final Arena arena;
    private final MemorySegment ring;

    private static final MethodHandle open;
    private static final MethodHandle malloc;
    private static final MethodHandle free;
    private static final MethodHandle seeAndClose;
    private static final MethodHandle close;
    private static final MethodHandle read_with_offset_buffer;

    static {
        SymbolLookup symbolLookup = SymbolLookup.libraryLookup("/home/david/cproject/libfilemanager.so", Arena.global());

        Linker linker = Linker.nativeLinker();
        SymbolLookup defaultedLookup = linker.defaultLookup();

        open = linker.downcallHandle(
                defaultedLookup.find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)
        );

        free = linker.downcallHandle(
                defaultedLookup.find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        malloc = linker.downcallHandle(
                defaultedLookup.find("malloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT)
        );

        close = linker.downcallHandle(
                defaultedLookup.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_INT)
        );

        seeAndClose = Linker.nativeLinker().downcallHandle(
                symbolLookup.find("see_and_close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS)
        );

        read_with_offset_buffer = Linker.nativeLinker().downcallHandle(
                symbolLookup.find("read_with_offset_buffer").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, JAVA_INT)
        );

    }

    LibUringLayer(int queue_depth, boolean polling) {
        this.arena = Arena.ofShared();

        int ret;
        if (polling) {
            MemorySegment ioup = arena.allocate(io_uring_params.layout());
            io_uring_params.flags(ioup, (1 << 1));
            int ThreadIdleTimeInMilliseconds = 2000;
            io_uring_params.sq_thread_idle(ioup, ThreadIdleTimeInMilliseconds);

            ring = arena.allocate(io_uring.layout());
            ret = liburingtest.io_uring_queue_init_params(queue_depth, ring, ioup);
        } else {
            ring = arena.allocate(io_uring.layout());
            ret = liburingtest.io_uring_queue_init(queue_depth, ring, 0);
        }

        if (ret < 0) {
            throw new RuntimeException("ring init ret = " + ret);
        }
    }

    int open(String path, int mode, int flags) {
        try {
            var StringBytes = path.getBytes();
            MemorySegment memorySegment = mallocWithCleaner(StringBytes.length);
            MemorySegment.copy(StringBytes, 0, memorySegment, JAVA_BYTE, 0, StringBytes.length);
            return (int) open.invokeExact(memorySegment, flags, mode);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void closeFile(int seg) {
        try {
            close.invokeExact(seg);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    MemorySegment malloc(int size) {
        try {
            return ((MemorySegment) malloc.invokeExact(size)).reinterpret(size);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    MemorySegment mallocWithCleaner(int size) {
        try {
            return ((MemorySegment) malloc.invokeExact(size)).reinterpret(size, AUTO, this::free);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void free(MemorySegment mem) {
        try {
            free.invoke(mem);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    MemorySegment prepareReadRequest(int fd, long bufferSize, long userDate, int offset) {
        try {
            return ((MemorySegment) read_with_offset_buffer.invokeExact(ring, fd, bufferSize, userDate, offset)).reinterpret(bufferSize);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void prepareWriteRequest(long userData, int fd, MemorySegment bufPtr, int offset) {
        MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
        liburingtest.io_uring_sqe_set_data_long(sqe, userData);
        liburingtest.io_uring_prep_write(sqe, fd, bufPtr, (int) bufPtr.byteSize(), offset);
    }

    void submit() {
        int ret = liburingtest.io_uring_submit(ring);

        if (ret < 0) {
            System.out.println("io_uring_submit " + ret);
        }
    }

    int waitAndSee() {
        try {
            return (int) seeAndClose.invokeExact(ring);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        liburingtest.io_uring_queue_exit(ring);
        try {
            arena.close();
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Session is acquired by")){
                // Can ignore, ring is closed by now
            }
        }
    }
}
