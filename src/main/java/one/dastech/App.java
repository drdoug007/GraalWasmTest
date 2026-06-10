package one.dastech;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("Loading Wasm module...");

        try (InputStream is = App.class.getResourceAsStream("/main.wasm")) {
            if (is == null) {
                System.err.println("Could not find main.wasm in resources.");
                return;
            }
            byte[] wasmBinary = is.readAllBytes();

            try (Context context = Context.newBuilder("wasm")
                    .option("wasm.Builtins", "wasi_snapshot_preview1")
                    .allowIO(true)
                    .build()) {
                Source source = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "main").build();
                Value mainModule = context.eval(source);
                Value mainInstance = mainModule.newInstance();
                Value exports = mainInstance.getMember("exports");
                Value mainFunc = exports.getMember("_start");
                try {
                    if (mainFunc != null && mainFunc.canExecute()) {
                        mainFunc.execute();
                    } else {
                        System.out.println("No _start function found or not executable.");
                    }
                } catch (PolyglotException e) {
                    if (e.isExit()) {
                        System.out.println("Wasm module exited with code: " + e.getExitStatus());
                    } else {
                        throw e;
                    }
                }
            } catch (PolyglotException e) {
                if (!e.isExit()) {
                    throw e;
                }
            }
        }
    }
}
