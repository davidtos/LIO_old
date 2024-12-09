package com.davidvlijmincx.newreader.FakeUser;

import com.davidvlijmincx.newreader.lib.JLibUring;
import com.davidvlijmincx.newreader.lib.ReadRequest;
import com.davidvlijmincx.newreader.lib.WriteRequest;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {

    public static void main(String[] args) {
        try (JLibUring JLibUring = new JLibUring(5, true)) {

            String myContent = "5555555";

            WriteRequest writeRequest = JLibUring.prepareWrite("./tmp_file", myContent.getBytes(), 0);
            JLibUring.submit();
            writeRequest.waitForWriteToFinish();

            ReadRequest readRequest = JLibUring.submitRead("./tmp_file_read", 10, 1);
            JLibUring.submit();
            System.out.println(UTF_8.decode(readRequest.getData().asByteBuffer()));
            readRequest.freeBuffer();
        }


    }
}
