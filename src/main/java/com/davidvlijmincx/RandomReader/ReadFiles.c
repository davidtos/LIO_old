#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <assert.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <liburing.h>

#define MAX_FILES 99999

// TODO: make this easier
// compile gcc -shared -o libfilemanager.so -fPIC ReadFiles.c
int file_descriptors[MAX_FILES];

// Function to open an array of files and return the number of successfully opened files
int* open_files(const char* file_paths[], int num_files) {
    for (int i = 0; i < num_files; i++) {
        file_descriptors[i] = open(file_paths[i], O_RDONLY);
        if (file_descriptors[i] == -1) {
            printf("Error: Could not open file %s - %s\n", file_paths[i], strerror(errno));
        } else {
          //  printf("Opened file: %s with file descriptor: %d\n", file_paths[i], file_descriptors[i]);
        }
    }

    return file_descriptors;
}

// Function to close files
void close_files(int num_files) {
    for (int i = 0; i < num_files; i++) {
        if (file_descriptors[i] != -1) {
            close(file_descriptors[i]);
        }
    }
}

void close_fds(int *fds, int count) {
    for (int i = 0; i < count; i++) {
        if (fds[i] >= 0) {        // Check if the file descriptor is valid
            close(fds[i]);         // Close the file descriptor
            fds[i] = -1;           // Optionally set it to -1 after closing
        }
    }
}

void queue_prepped(struct io_uring *ring, int fdPos, char *buff, int size, void *user_data) {

    struct io_uring_sqe *sqe;
    sqe = io_uring_get_sqe(ring);
    sqe->flags |= IOSQE_FIXED_FILE;
    io_uring_prep_read(sqe, fdPos, buff, size, 0);

    io_uring_sqe_set_data(sqe, user_data);

}

void queue_prepped_offset(struct io_uring *ring, int fdPos, char *buff, int size, void *user_data, int offset) {

    struct io_uring_sqe *sqe;
    sqe = io_uring_get_sqe(ring);
    sqe->flags |= IOSQE_FIXED_FILE;
    io_uring_sqe_set_data(sqe, user_data);
    io_uring_prep_read(sqe, fdPos, buff, size, offset);

}