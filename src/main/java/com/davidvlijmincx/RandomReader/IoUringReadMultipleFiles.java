package com.davidvlijmincx.RandomReader;


import com.davidvlijmincx.generated.io.uring.io_uring;
import com.davidvlijmincx.generated.io.uring.io_uring_params;
import com.davidvlijmincx.generated.io.uring.liburingtest;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;


public class IoUringReadMultipleFiles implements AutoCloseable {


    public final Arena arena;
    private final MemorySegment ring;
    MethodHandle open;
    MethodHandle close;

    public IoUringReadMultipleFiles(int qd) {
        arena = Arena.ofConfined();

        MemorySegment ioup = arena.allocate(io_uring_params.layout());
        io_uring_params.flags(ioup, (1 << 1));
        int ThreadIdleTimeInMilliseconds = 2000;
        io_uring_params.sq_thread_idle(ioup, ThreadIdleTimeInMilliseconds);

        // Init ring
        ring = arena.allocate(io_uring.layout());
        int ret = liburingtest.io_uring_queue_init_params(qd, ring, ioup);

        Linker linker = Linker.nativeLinker();
        SymbolLookup defaultLookup = linker.defaultLookup();

        open = linker.downcallHandle(
                defaultLookup.find("open").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
        );

        close = linker.downcallHandle(
                defaultLookup.find("close").orElseThrow(),
                FunctionDescriptor.ofVoid(JAVA_INT)
        );

        if (ret < 0) {
            System.out.println("ring init ret = " + ret);
        }
    }


    public void createReadRequest(MemorySegment buff, int fileSize, int fdPosition, long data) {
        // Create read request, set the polling flag
        MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
        liburingtest.io_uring_sqe_set_flags(sqe, 1);

        // prepare the read
        int offset = 0;
        liburingtest.io_uring_prep_read(sqe, fdPosition, buff, fileSize, offset);

        // set user data, so it's possible to match requests with cqe
        // Added this method myself to the generated to code
        // The generated method was slower and needed an extra segment
        liburingtest.io_uring_sqe_set_data_long(sqe, data);

    }


    public void createReadRequestWithOffset(MemorySegment buff, int fileSize, int fdPosition, long data,int offset) {
        // Create read request, set the polling flag
        MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
        liburingtest.io_uring_sqe_set_flags(sqe, 1);

        // prepare the read
        liburingtest.io_uring_prep_read(sqe, fdPosition, buff, fileSize, offset);

        // set user data, so it's possible to match requests with cqe
        // Added this method myself to the generated to code
        // The generated method was slower and needed an extra segment
        liburingtest.io_uring_sqe_set_data_long(sqe, data);

    }

    public void createReadRequestDirectly(MethodHandle queue_prepped, int fdPosition, MemorySegment buff, int fileSize, long data) throws Throwable {
        queue_prepped.invoke(ring, fdPosition, buff, fileSize, data);
    }

    public void createReadRequestDirectlyWithOffset(MethodHandle queue_prepped, int fdPosition, MemorySegment buff, int fileSize, long data, int offset) throws Throwable {
        queue_prepped.invoke(ring, fdPosition, buff, fileSize, data, offset);
    }

    public void submit() {
        // Submit the queue to be picked up
        int ret = liburingtest.io_uring_submit(ring);

        if (ret < 0) {
            System.out.println("io_uring_submit " + ret);
        }
    }

    public void registerFds(FileAtt[] fdSet, MemorySegment fds) {
        // Create an area to of fds to register later

        for (int i = 0; i < fdSet.length; i++) {
            fds.setAtIndex(JAVA_INT, i, fdSet[i].fd);
        }

        // register the file descriptor
        liburingtest.io_uring_register_files(ring, fds, fdSet.length);
    }

    public void passFdDirectly(int length, MemorySegment fds) {
        // register the file descriptor
        liburingtest.io_uring_register_files(ring, fds, length);
    }

    public int getFd(String path) throws Throwable {
        // Get file descriptor
        int mode = 0;
        MemorySegment pathSegment = arena.allocateFrom(path);
        return (int) open.invoke(pathSegment, mode);
    }

    public void closeFd(int fd) throws Throwable {
        close.invoke(fd);
    }

    public void close() {
        liburingtest.io_uring_queue_exit(ring);
        arena.close();
    }


    public MemorySegment see(MemorySegment ptr) {
        int ret;
        ret = liburingtest.io_uring_wait_cqe(ring, ptr);
        if (ret != 0) {
            throw new RuntimeException("io_uring_wait_cqe failed with code: " + ret);
        }
        return ptr;
    }


    public String getReadData(MemorySegment buff) {
        // Get the user data that was set earlier to match requests
        return buff.getString(0);
    }


    public void seen(MemorySegment cqePtr) {
        liburingtest.io_uring_cqe_seen(ring, cqePtr);
    }


}


