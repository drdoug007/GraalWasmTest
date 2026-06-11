package one.dastech;

import jakarta.annotation.PreDestroy;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AsciiArtService {

    private static final Logger log = LoggerFactory.getLogger(AsciiArtService.class);

    // Explicit lock to safeguard single-threaded WebAssembly linear memory
    private final ReentrantLock wasmLock = new ReentrantLock();

    private Value getASCIIArtFunc;
    private Value mallocFunc;
    private Value memory;
    private Context context;
    private Value free;

    public AsciiArtService() {
        // Safe stream resolution utilizing the current class loader space
        try (InputStream is = AsciiArtService.class.getResourceAsStream("/main.wasm")) {
            if (is == null) {
                log.error("Could not find main.wasm in resources.");
                throw new IllegalStateException("Missing core main.wasm dependency artifact.");
            }
            byte[] wasmBinary = is.readAllBytes();

            try {
                context = Context.newBuilder("wasm")
                        .option("wasm.Builtins", "wasi_snapshot_preview1")
                        .allowIO(true)
                        .build();

                Source source = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "main").build();
                Value asciiartModule = context.eval(source);
                Value mainInstance = asciiartModule.newInstance();
                Value exports = mainInstance.getMember("exports");

                Value initialize = exports.getMember("_initialize");
                if (initialize != null && initialize.canExecute()) {
                    try {
                        initialize.execute();
                    } catch (PolyglotException e) {
                        if (e.isExit()) {
                            log.error("Wasm module failed during initialization with code: {}", e.getExitStatus());
                        } else {
                            throw e;
                        }
                    }
                }

                getASCIIArtFunc = exports.getMember("GetASCIIArt");
                mallocFunc = exports.getMember("malloc");
                memory = exports.getMember("memory");
                free = exports.getMember("free");

            } catch (PolyglotException e) {
                log.error("Polyglot Engine compilation crash: {}", e.getMessage());
                if (!e.isExit()) {
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    public String generateAsciiArt(String input, String fontName) {
        if (getASCIIArtFunc == null || mallocFunc == null || memory == null || free == null) {
            log.error("Required Wasm exports not found.");
            throw new RuntimeException("Wasm module functions or memory not initialized properly.");
        }

        // Lock access so multiple concurrent HTTP requests process sequentially without memory corruption
        wasmLock.lock();

        int inputPtr = 0;
        int resultPtr = 0;

        try {
            // Construct JSON payload
            String jsonPayload = String.format("{\"text\":\"%s\",\"font\":\"%s\"}",
                    input.replace("\n", "\\n").replace("\"", "\\\""),
                    fontName
            );
            byte[] inputBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            int inputSize = inputBytes.length;

            // Allocate memory space inside WebAssembly
            Value ptrValue = mallocFunc.execute(inputSize);
            inputPtr = ptrValue.asInt();

            for (int i = 0; i < inputSize; i++) {
                memory.writeBufferByte(inputPtr + i, inputBytes[i]);
            }

            // Execute processing routine
            Value resValue = getASCIIArtFunc.execute(inputPtr, inputSize);
            long resultPacked = resValue.asLong();
            resultPtr = (int) (resultPacked & 0xFFFFFFFFL);
            int resSize = (int) (resultPacked >>> 32);

            // Read the result string from Wasm memory
            byte[] resBytes = new byte[resSize];
            for (int i = 0; i < resSize; i++) {
                // Correct way to read a single byte out of GraalWasm linear memory
                resBytes[i] = memory.getArrayElement(resultPtr + i).asByte();
            }

            return new String(resBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error executing WebAssembly pipeline", e);
            throw new RuntimeException(e);
        } finally {
            // Guarantee memory de-allocation before releasing the thread lock
            try {
                if (inputPtr != 0) {
                    free.execute(inputPtr);
                }
                if (resultPtr != 0) {
                    free.execute(resultPtr);
                }
            } finally {
                wasmLock.unlock();
            }
        }
    }
}
