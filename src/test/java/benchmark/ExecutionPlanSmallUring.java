package benchmark;

import com.davidvlijmincx.newreader.QuickReader;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;

@State(Scope.Thread)
public class ExecutionPlanSmallUring {

    QuickReader q;

    @Setup
    public void setup() {
       // SymbolLookup SYMBOL_LOOKUP = SymbolLookup.libraryLookup("/home/david/IdeaProjects/C_project/libfilemanager.so", Arena.global());
        q = new QuickReader(2500,true);

    }

    @TearDown
    public void tearDown() throws Throwable {
        q.close();
    }

}
