package com.davidvlijmincx.complete;

import com.davidvlijmincx.generated.src.org.libfuse.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

import static com.davidvlijmincx.generated.src.org.libfuse.fuse_h.C_POINTER;

public class FuseMain {

    static List<String> directories = new ArrayList<>();
    static List<String> files = new ArrayList<>();
    static Map<String, String> filesContent = new HashMap<>();
    static Arena fuseScope = null;

    public static void main(String[] args) {
        args = new String[]{"-f", "-d", "/home/david/test/"};

        try (var arena = Arena.ofShared()) {
            MemorySegment pointers = arena.allocate(ValueLayout.ADDRESS, args.length);
            fuseScope = arena;

            for (int i = 0; i < args.length; i++) {
                MemorySegment cString = arena.allocateFrom(args[i]);
                pointers.setAtIndex(ValueLayout.ADDRESS, i, cString);
            }

            MemorySegment operationsMemorySegment = fuse_operations.allocate(arena);

            fuse_operations.getattr(operationsMemorySegment, fuse_operations.getattr.allocate(FuseMain::getAttr, arena));
            fuse_operations.readdir(operationsMemorySegment, fuse_operations.readdir.allocate(FuseMain::readDir, arena));
            fuse_operations.read(operationsMemorySegment, fuse_operations.read.allocate(FuseMain::read, arena));
            fuse_operations.mkdir(operationsMemorySegment, fuse_operations.mkdir.allocate(FuseMain::doMkdir, arena));
            fuse_operations.mknod(operationsMemorySegment, fuse_operations.mknod.allocate(FuseMain::doMknod, arena));
            fuse_operations.write(operationsMemorySegment, fuse_operations.write.allocate(FuseMain::doWrite, arena));


            Linker linker = Linker.nativeLinker();
            SymbolLookup symbolLookup = SymbolLookup.libraryLookup("/lib/x86_64-linux-gnu/libfuse3.so.3", arena);

            FunctionDescriptor descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    C_POINTER,
                    C_POINTER,
                    ValueLayout.JAVA_LONG,
                    C_POINTER);

            MethodHandle fuse_main_real = linker.downcallHandle(
                    symbolLookup.find("fuse_main_real").orElseThrow(), descriptor);

            fuse_main_real.invoke(args.length, pointers, operationsMemorySegment, operationsMemorySegment.byteSize(), MemorySegment.NULL);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isDir(String path) {
        return directories.contains(path);
    }

    static void addFile(String filename) {
        files.add(filename);
        filesContent.put(filename, "");
    }

    static boolean isFile(String path) {
        return files.contains(path);
    }

    public static int getAttr(MemorySegment path, MemorySegment statMemorySegment, MemorySegment fi) {
        String jPath = path.getString(0);

        int S_IFDIR = 0040000; /* directory */
        int S_IFREG = 0100000; /* regular */

        // setting the stat atim (last access time)
        Instant now = Instant.now();
        timespec.tv_sec(stat.st_atim(statMemorySegment), now.getEpochSecond());
        timespec.tv_nsec(stat.st_atim(statMemorySegment), now.getNano());

        // setting the stat mtim (last modify time)
        now = Instant.now();
        timespec.tv_sec(stat.st_mtim(statMemorySegment), now.getEpochSecond());
        timespec.tv_nsec(stat.st_mtim(statMemorySegment), now.getNano());

        stat.st_uid(statMemorySegment, 1000);
        stat.st_gid(statMemorySegment, 1000);

        if ("/".equals(jPath) || isDir(jPath.substring(1))) {
            stat.st_mode(statMemorySegment, (short) (S_IFDIR | 0755));
            stat.st_nlink(statMemorySegment, 2);
        } else if (isFile(jPath.substring(1))) {
            stat.st_mode(statMemorySegment, S_IFREG | 0644);
            stat.st_nlink(statMemorySegment, 1);
            stat.st_size(statMemorySegment, filesContent.get(jPath.substring(1)).getBytes().length);
        } else {
            return -2;
        }

        return 0;
    }

    public static int readDir(MemorySegment path, MemorySegment buffer, MemorySegment filler, long offset, MemorySegment fileInfo, int flags) {

        fuse_fill_dir_t.invoke(filler, buffer, fuseScope.allocateFrom("."), MemorySegment.NULL, 0, 0);
        fuse_fill_dir_t.invoke(filler, buffer, fuseScope.allocateFrom(".."), MemorySegment.NULL, 0, 0);

        String jPath = path.getString(0);

        if ("/".equals(jPath)) {
            for (String p : directories) {
                fuse_fill_dir_t.invoke(filler, buffer, fuseScope.allocateFrom(p), MemorySegment.NULL, 0, 0);
            }

            for (String p : files) {
                fuse_fill_dir_t.invoke(filler, buffer, fuseScope.allocateFrom(p), MemorySegment.NULL, 0, 0);
            }
        }

        return 0;
    }

    public static int read(MemorySegment path, MemorySegment buffer, long size, long offset, MemorySegment fileInfo) {

        String jPath = path.getString(0).substring(1);

        if (!isFile(jPath)) {
            return -1;
        }

        byte[] selected = filesContent.get(jPath).getBytes();

        ByteBuffer byteBuffer = buffer.reinterpret(size).asByteBuffer();

        byte[] src = Arrays.copyOfRange(selected, Math.toIntExact(offset), Math.toIntExact(size));
        byteBuffer.put(src);

        return src.length;
    }

    static int doMkdir(MemorySegment path, int mode) {
        String jPath = path.getString(0);
        directories.add(jPath.substring(1));
        return 0;
    }

    static int doMknod(MemorySegment path, int mode, long rdev) {
        String jPath = path.getString(0);
        addFile(jPath.substring(1));
        return 0;
    }

    static int doWrite(MemorySegment path, MemorySegment buffer, long size, long offset, MemorySegment info) {
        String jPath = path.getString(0).substring(1);
        filesContent.put(jPath, buffer.getString(offset, java.nio.charset.StandardCharsets.UTF_8));
        return Math.toIntExact(size);
    }


}