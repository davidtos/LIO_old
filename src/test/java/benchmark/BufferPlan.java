package benchmark;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@State(Scope.Benchmark)
public class BufferPlan {

    public static final int SIZE = 1024;
    MemorySegment segment;
    ByteBuffer buffer;
    ByteBuffer directBuffer;


    @Setup
    public void setup() {
        segment = Arena.ofAuto().allocate(SIZE);
        buffer = ByteBuffer.allocate(SIZE);
        directBuffer = ByteBuffer.allocateDirect(SIZE);

        String string = getString(SIZE -1);

        segment.setString(0, string);
        buffer.put(string.getBytes());
        directBuffer.put(string.getBytes());
    }



    String getString(int length) {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 ";
        StringBuilder salt = new StringBuilder();
        Random random = new Random(315315153152442L);

        while (salt.length() < length) {
            int index = random.nextInt(0, SALTCHARS.length()-1);
            salt.append(SALTCHARS.charAt(index));
        }

        return salt.toString();
    }

}
