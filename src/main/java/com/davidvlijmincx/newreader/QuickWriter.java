package com.davidvlijmincx.newreader;

import com.davidvlijmincx.generated.io.uring.io_uring;
import com.davidvlijmincx.generated.io.uring.io_uring_params;
import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

public class QuickWriter {

    final Arena arena;
    private final MemorySegment ring;

    private static final MethodHandle open;
    private static final MethodHandle malloc;
    private static final MethodHandle free;
    private static final MethodHandle seeAndClose;
    private static final MethodHandle close;

    static {
        SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", Arena.global());

        Linker LINKER = Linker.nativeLinker();

        open = LINKER.downcallHandle(
                LINKER.defaultLookup().find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)
        );


        free = LINKER.downcallHandle(
                LINKER.defaultLookup().find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        malloc = LINKER.downcallHandle(
                LINKER.defaultLookup().find("malloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT)
        );

        close = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_INT)
        );

        seeAndClose = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("see_and_close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS)
        );
    }

    public QuickWriter(int QD, boolean polling) {
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
            MemorySegment memorySegment = mallocWithCleaner(StringBytes.length);
            MemorySegment.copy(StringBytes, 0, memorySegment, JAVA_BYTE, 0, StringBytes.length);
            return (int) open.invokeExact(memorySegment, flags, mode);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void closeFile(int seg) {
        try {
            close.invokeExact(seg);
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

    public MemorySegment mallocWithCleaner(int size) {
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

    public void submitWriteRequest(long userData, int fd, MemorySegment bufPtr, int offset) {
        MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
        liburingtest.io_uring_sqe_set_data_long(sqe,  userData);
        liburingtest.io_uring_prep_write(sqe, fd, bufPtr, (int) bufPtr.byteSize(), offset);
    }

    public void submit() {
        int ret = liburingtest.io_uring_submit(ring);

        if (ret < 0) {
            System.out.println("io_uring_submit " + ret);
        }
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
