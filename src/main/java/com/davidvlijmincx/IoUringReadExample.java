package com.davidvlijmincx;

import com.davidvlijmincx.generated.io.uring.*;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

public class IoUringReadExample {

    static int QD = 4;
    static String path = "./tmp_file";

    public static void main(String[] args) throws Throwable {
        int fd, ret;
        int fileSize = (int) new File(path).length();

        try (var arena = Arena.ofConfined()) {
            final var linker = Linker.nativeLinker();
            final var defaultLookup = linker.defaultLookup();

            // Create and set ring parameters
            MemorySegment ioup = arena.allocate(io_uring_params.layout());
            io_uring_params.flags(ioup, (1 << 1));
            int ThreadIdleTimeInMilliseconds = 2000;
            io_uring_params.sq_thread_idle(ioup, ThreadIdleTimeInMilliseconds);

            // Init ring
            MemorySegment ring = arena.allocate(io_uring.layout());
            ret = liburingtest.io_uring_queue_init_params(QD, ring, ioup);

            if (ret < 0) {
                System.out.println("ring init ret = " + ret);
            }

            // open file
            MethodHandle open = linker.downcallHandle(
                    defaultLookup.find("open").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
            );

            // Get file descriptor
            int mode = 0;
            MemorySegment pathSegment = arena.allocateFrom(path);
            fd = (int) open.invoke(pathSegment, mode);

            // Create an area to of fds to register later
        //    MemorySegment fds = arena.allocate(JAVA_INT, 1);
        //    fds.setAtIndex(JAVA_INT, 0, fd);

            // register the file descriptor
       //     liburingtest.io_uring_register_files(ring, fds, 1);

            // Create read request, set the polling flag
            MemorySegment sqe = liburingtest.io_uring_get_sqe(ring);
            liburingtest.io_uring_sqe_set_flags(sqe, 1 << 0);

            // Buffer to place to content of the file.
            MemorySegment buff = arena.allocate(JAVA_CHAR, fileSize);

            // prepare the read
            int fdPosition = 0; // which fd from the array to use
            int offset = 0;
            liburingtest.io_uring_prep_read(sqe, fd, buff, fileSize, offset);

            // set user data, so it's possible to match requests with cqe
            liburingtest.io_uring_sqe_set_data_long(sqe, 12345L);

            // Submit the queue to be picked up
            ret = liburingtest.io_uring_submit(ring);

            if (ret < 0) {
                System.out.println("io_uring_submit " + ret);
            }

            // wait for something to be in the completion queue
            MemorySegment cqePtr = arena.allocate(io_uring_cqe.layout());
            ret = liburingtest.io_uring_wait_cqe(ring, cqePtr);
            if (ret != 0) {
                throw new RuntimeException("io_uring_wait_cqe failed with code: " + ret);
            }

            // The buffer we submitted should be filled now
            System.out.printf("file content: %s%n", buff.getString(0));

            // print status
            System.out.printf("cqe result: %d%n", io_uring_cqe.res(cqePtr));

            // Get the user data that was set earlier to match requests
            MemorySegment usersData = liburingtest.io_uring_cqe_get_data(cqePtr);

            System.out.printf("Got finished job  = %d%n", usersData.get(JAVA_LONG,0));
            // mark as the cqe consumed
            liburingtest.io_uring_cqe_seen(ring, cqePtr);

            // close the resources used for this ring
            liburingtest.io_uring_queue_exit(ring);

            // close the file
            MethodHandle close = linker.downcallHandle(
                    defaultLookup.find("close").get(),
                    FunctionDescriptor.ofVoid(JAVA_INT)
            );

            close.invoke(fd);

        }


    }
}
