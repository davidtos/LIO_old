package com.davidvlijmincx.newreader;

import com.davidvlijmincx.generated.io.uring.io_uring;

import com.davidvlijmincx.generated.io.uring.io_uring_cqe;
import com.davidvlijmincx.generated.io.uring.io_uring_params;

import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class QuickReader implements AutoCloseable {

    private static final MethodHandle open_direct;
    private static final MethodHandle openC;
    private static final MethodHandle close;
    private static final MethodHandle queue_prepped_offset;
    private static final MethodHandle read_with_offset_buffer;
    private static final MethodHandle seeAndClose;
    private static final MethodHandle malloc;
    private static final MethodHandle mallocLong;
    private static final MethodHandle alignedAlloc;
    private static final MethodHandle free;
    private static final MethodHandle freeLong;

    private final MemorySegment ring;
    private final Arena arena;

    static {
        SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", Arena.global());
        Linker LINKER = Linker.nativeLinker();

        open_direct = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("open_direct").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        openC = LINKER.downcallHandle(
                LINKER.defaultLookup().find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
        );


        malloc = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("malloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT)
        );

        mallocLong = LINKER.downcallHandle(
                LINKER.defaultLookup().find("malloc").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG)
        );

        alignedAlloc = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("aligned_alloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT)
        );


        free = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        freeLong = LINKER.downcallHandle(
                LINKER.defaultLookup().find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_LONG)
        );

        close = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_INT)
        );

        queue_prepped_offset = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("read_with_offset").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, JAVA_INT)
        );


        read_with_offset_buffer = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("read_with_offset_buffer").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, JAVA_INT)
        );


        seeAndClose = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("see_and_close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS)
        );
    }


    public QuickReader(int QD, boolean polling) {
        arena = Arena.ofShared();


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


    public MemorySegment openFile(String path) {
        try {
            MemorySegment memorySegment = arena.allocateFrom(path);
            return (MemorySegment) open_direct.invokeExact(memorySegment);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int open(String path) {
        try {
//            MemorySegment memorySegment = arena.allocateFrom(path);
            var StringBytes = path.getBytes();
             MemorySegment memorySegment = malloc(StringBytes.length);
            MemorySegment.copy(StringBytes, 0,memorySegment, JAVA_BYTE, 0,StringBytes.length);


            return (int) openC.invokeExact(memorySegment,0000000);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment mallocOpenFile(String path) {
        try {

            byte[] bytes = path.getBytes();
            int length = bytes.length;
            MemorySegment memorySegment = malloc(length + 1);

            MemorySegment.copy(bytes, 0, memorySegment, ValueLayout.JAVA_BYTE, 0, length);
            memorySegment.set(ValueLayout.JAVA_BYTE, (length), (byte) 0);

            // MemorySegment memorySegment = arena.allocateFrom(path);
            var o = (MemorySegment) open_direct.invokeExact(memorySegment);

            free(memorySegment);
            return o;

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

    public MemorySegment alignedAlloc(int alignment, int size) {
        try {
            return ((MemorySegment) alignedAlloc.invokeExact(alignment,size)).reinterpret(size);
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


    public void submit() {
        // Submit the queue to be picked up
        int ret = liburingtest.io_uring_submit(ring);

        if (ret < 0) {
            System.out.println("io_uring_submit " + ret);
        }
    }

    public void submitReadRequest(int fd, MemorySegment buffer, long bufferSize, long userDate, int offset) {
        try {
            queue_prepped_offset.invokeExact(ring, fd, buffer, bufferSize, userDate, offset);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment submitReadRequest2(int fd, long bufferSize, long userDate, int offset) {
        try {
            return ((MemorySegment) read_with_offset_buffer.invokeExact(ring, fd, bufferSize, userDate, offset)).reinterpret(bufferSize);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment readFromCompletion() {
        var pntr = arena.allocate(io_uring_cqe.layout());
        liburingtest.io_uring_wait_cqe(ring, pntr);
        return pntr;
    }

    public void seen(MemorySegment cqePtr) {
        liburingtest.io_uring_cqe_seen(ring, cqePtr);
    }

    public int waitAndSee() {
        try {
            return (int) seeAndClose.invokeExact(ring);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        liburingtest.io_uring_queue_exit(ring);
        arena.close();
    }
}
