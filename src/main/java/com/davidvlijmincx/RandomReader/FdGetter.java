package com.davidvlijmincx.RandomReader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class FdGetter {

    public static MemorySegment openFiles(String[] filePaths, Arena arena, MethodHandle openFilesHandle, MemorySegment pathsArray) throws Throwable {

        for (int i = 0; i < filePaths.length; i++) {
            pathsArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(filePaths[i]));
        }

        return (MemorySegment) openFilesHandle.invoke(pathsArray, filePaths.length);
    }

}

