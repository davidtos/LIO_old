package com.davidvlijmincx.newreader.FakeUser;

import com.davidvlijmincx.newreader.lib.LibLayer;
import com.davidvlijmincx.newreader.lib.ReadRequest;
import com.davidvlijmincx.newreader.lib.WriteRequest;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {

    public static void main(String[] args) {
        try (LibLayer libLayer = new LibLayer(5, true)) {

            String myContent = "5555555";

            WriteRequest writeRequest = libLayer.prepareWrite("./tmp_file", myContent.getBytes(), 0);
            libLayer.submit();
            writeRequest.waitForWriteToFinish();

            ReadRequest readRequest = libLayer.submitRead("./tmp_file_read", 10, 1);
            libLayer.submit();
            System.out.println(UTF_8.decode(readRequest.getData().asByteBuffer()));
            readRequest.freeBuffer();
        }


    }
}
