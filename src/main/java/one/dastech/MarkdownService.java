package one.dastech;

import jakarta.annotation.PreDestroy;
import org.graalvm.polyglot.Context;
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
public class MarkdownService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownService.class);
    private final ReentrantLock wasmLock = new ReentrantLock();

    private final Context context;
    private final Value convertMarkdownFunc;
    private final Value mallocFunc;
    private final Value freeFunc;
    private final Value memory;

    public MarkdownService() {
        try (InputStream is = MarkdownService.class.getResourceAsStream("/main.wasm")) {
            if (is == null) {
                throw new IllegalStateException("Could not find markdown.wasm in resources.");
            }
            byte[] wasmBinary = is.readAllBytes();

            context = Context.newBuilder("wasm")
                    .option("wasm.Builtins", "wasi_snapshot_preview1")
                    .allowIO(true)
                    .build();

            Source source = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "markdown").build();
            Value module = context.eval(source);
            Value instance = module.newInstance();
            Value exports = instance.getMember("exports");

            // Initialize the Go WASI runtime
            Value initialize = exports.getMember("_initialize");
            if (initialize != null && initialize.canExecute()) {
                initialize.execute();
            }

            convertMarkdownFunc = exports.getMember("ConvertMarkdown");
            mallocFunc = exports.getMember("malloc");
            freeFunc = exports.getMember("free");
            memory = exports.getMember("memory");

        } catch (IOException e) {
            throw new RuntimeException("Failed to read markdown WebAssembly binary", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    public String toHtml(String markdownText) {
        if (convertMarkdownFunc == null || mallocFunc == null || memory == null || freeFunc == null) {
            throw new IllegalStateException("Markdown WebAssembly module not ready.");
        }

        wasmLock.lock();
        int inputPtr = 0;
        int resultPtr = 0;

        try {
            // Escape structural quotes and newlines for safe JSON packaging
            String jsonPayload = String.format("{\"markdown\":\"%s\"}",
                    markdownText.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
            );
            byte[] inputBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            int inputSize = inputBytes.length;

            // Allocate linear memory
            inputPtr = mallocFunc.execute(inputSize).asInt();

            // Direct byte-buffer memory write sequence
            for (int i = 0; i < inputSize; i++) {
                memory.writeBufferByte(inputPtr + i, inputBytes[i]);
            }

            // Execute processing routine in Go context
            long packedResult = convertMarkdownFunc.execute(inputPtr, inputSize).asLong();
            resultPtr = (int) (packedResult & 0xFFFFFFFFL);
            int resultSize = (int) (packedResult >>> 32);

            // Read raw computed HTML back out
            byte[] resBytes = new byte[resultSize];
            for (int i = 0; i < resultSize; i++) {
                resBytes[i] = memory.readBufferByte(resultPtr + i);
            }

            return new String(resBytes, StandardCharsets.UTF_8);

        } finally {
            try {
                if (inputPtr != 0) freeFunc.execute(inputPtr);
                if (resultPtr != 0) freeFunc.execute(resultPtr);
            } finally {
                wasmLock.unlock();
            }
        }
    }
}