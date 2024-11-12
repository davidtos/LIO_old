package com.davidvlijmincx.newreader;

import com.davidvlijmincx.generated.io.uring.io_uring;
import com.davidvlijmincx.generated.io.uring.io_uring_cqe;
import com.davidvlijmincx.generated.io.uring.io_uring_params;
import com.davidvlijmincx.generated.io.uring.liburingtest;
import com.davidvlijmincx.generated.src.org.libfuse._fuse_off_t_must_be_64bit_dummy_struct;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class QuickReader implements AutoCloseable {

    private final MethodHandle open_direct;
    private final MethodHandle close;
    private final MethodHandle queue_prepped_offset;
    private final MethodHandle seeAndClose;
    private final MethodHandle malloc;
    private final MethodHandle free;

    private final MemorySegment ring;
    private final Arena arena;


    public QuickReader(int QD, boolean polling, SymbolLookup SYMBOL_LOOKUP) {
        arena = Arena.ofConfined();

        Linker LINKER = Linker.nativeLinker();
        open_direct = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("open_direct").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );


        malloc = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("malloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT)
        );


        free = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        close = LINKER.downcallHandle(
                SYMBOL_LOOKUP.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        queue_prepped_offset = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("read_with_offset").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, JAVA_INT)
        );

        seeAndClose = Linker.nativeLinker().downcallHandle(
                SYMBOL_LOOKUP.find("see_and_close").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ValueLayout.ADDRESS)
        );

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

    public void closeFile(MemorySegment seg) {
        try {
            close.invokeExact(seg);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment malloc(int size){
        try {
            return ((MemorySegment) malloc.invokeExact(size)).reinterpret(size);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void free(MemorySegment mem){
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

    public void submitReadRequest(MemorySegment fd, MemorySegment buffer, long bufferSize, long userDate, int offset) {
        try {
            queue_prepped_offset.invokeExact(ring, fd, buffer, bufferSize, userDate, offset);
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

    public int waitAndSee(){
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
