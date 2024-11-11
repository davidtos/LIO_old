package com.davidvlijmincx.newreader;

import com.davidvlijmincx.setup.BenchmarkFiles;
import com.davidvlijmincx.setup.FileTooReadData;

public class QuickReader {


    public static void main(String[] args) {

        for (FileTooReadData s : BenchmarkFiles.filesTooRead) {
            System.out.println(s.sPath());
        }

    }
}
