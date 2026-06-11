# GraalWasmTest

This project demonstrates how to compile a Go application into WebAssembly (Wasm) and execute it within a Java application using the GraalVM Polyglot API.

## Project Overview

The project consists of:
- A **Go application** (`src/main/go/main.go`) that uses the `go-figure` library to generate ASCII art.
- A **Java application** (`src/main/java/one/dastech/App.java`) that loads the compiled `.wasm` file and runs it using GraalVM's Wasm engine.
- A **Maven configuration** (`pom.xml`) that orchestrates the build process, including compiling the Go code to Wasm.

## Prerequisites

- **Java 25** (or compatible version supporting GraalVM SDK 25.1.0-SNAPSHOT).
- **Maven**.
- **Go** (installed at `/opt/homebrew/bin/go` or updated in `pom.xml`).
- **GraalVM SDK 25.1.0-SNAPSHOT** (installed in your local Maven repository).

## Project Structure

- `src/main/go/`: Contains the Go source code and module definitions.
- `src/main/java/`: Contains the Java wrapper application.
- `src/main/resources/`: Stores the compiled `main.wasm` module.
- `pom.xml`: Handles dependencies and build automation.
- `AGENTS.md`: Tracks the specific GraalVM version used.

## Build Instructions

To build the project, run:

```bash
mvn clean compile
```

This command will:
1. Initialize and tidy the Go module.
2. Compile the Go source code into a WebAssembly module (`main.wasm`) using `GOOS=wasip1 GOARCH=wasm`.
3. Compile the Java application.

## Running the Application

To execute the Spring Boot application and see the ASCII art output from the Wasm module, use the following Maven command:

```bash
mvn spring-boot:run
```

This command is configured to pass the necessary JVM flags (like `--enable-native-access=ALL-UNNAMED`) via the `spring-boot-maven-plugin` to support GraalVM Polyglot features.

## Technical Details

- **WASI Support**: The Go application is compiled for the `wasip1` target. The Java host enables `wasi_snapshot_preview1` built-ins to provide the necessary system calls.
- **GraalVM Polyglot**: Uses the latest `org.graalvm.polyglot` API to manage the Wasm execution context and memory.
- **ASCII Art**: Powered by the [`go-figure`](https://github.com/common-nighthawk/go-figure) Go library.
