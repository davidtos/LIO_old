package benchmark;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Benchmark)
public class ExecutionPlan {

    String[] files;

    @Setup
    public void setup() {
        List<String> strings = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            strings.add("./TestFiles/tmp_file" + i);
        }

        files = strings.toArray(new String[0]);

    }

}
