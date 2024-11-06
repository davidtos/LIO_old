package com.davidvlijmincx.RandomReader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;


public class FdGetter {

    public static MemorySegment openFiles(String[] filePaths, Arena arena, MethodHandle openFilesHandle, MemorySegment pathsArray) throws Throwable {

        for (int i = 0; i < filePaths.length; i++) {
            pathsArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(filePaths[i]));
        }

        MemorySegment fdPointer = (MemorySegment) openFilesHandle.invoke(pathsArray, filePaths.length);

        return fdPointer;
    }

    // FFM API wrapper for the close_files function
    public static void closeFiles(String[] filePaths, MethodHandle closeFilesHandle, MemorySegment fds) throws Throwable {
        closeFilesHandle.invoke(fds, filePaths.length);
    }
}

