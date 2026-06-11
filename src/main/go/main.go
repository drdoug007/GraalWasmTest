package main

import (
	"bytes"
	"encoding/json"
	"strings"
	"sync"
	"unsafe"

	"github.com/common-nighthawk/go-figure"
	"github.com/yuin/goldmark"
)

func main() {}

// --- REQUEST STRUCTS ---

type ArtRequest struct {
	Text string `json:"text"`
	Font string `json:"font"`
}

type MarkdownRequest struct {
	Markdown string `json:"markdown"`
}

// --- GLOBAL ALLOC TRACKER (Shared by all features) ---

var (
	allocMutex sync.Mutex
	allocs     = make(map[uint32][]byte)
)

// --- FEATURE 1: ASCII ART ---

//go:wasmexport GetASCIIArt
func GetASCIIArt(ptr, size uint32) uint64 {
	jsonStr := getString(ptr, size)
	var req ArtRequest
	if err := json.Unmarshal([]byte(jsonStr), &req); err != nil {
		req.Text = "Error: Invalid JSON"
		req.Font = ""
	}

	lines := strings.Split(req.Text, "\n")
	var artBuilder strings.Builder
	for _, line := range lines {
		if line == "" {
			artBuilder.WriteString("\n")
			continue
		}
		myFigure := figure.NewFigure(line, req.Font, true)
		artBuilder.WriteString(myFigure.String())
		artBuilder.WriteString("\n")
	}

	resBytes := []byte(artBuilder.String())
	resPtr, resSize := bytesToPtr(resBytes)

	allocMutex.Lock()
	allocs[resPtr] = resBytes
	allocMutex.Unlock()

	return uint64(resSize)<<32 | uint64(resPtr)
}

// --- FEATURE 2: MARKDOWN ---

//go:wasmexport ConvertMarkdown
func ConvertMarkdown(ptr, size uint32) uint64 {
	jsonStr := getString(ptr, size)
	var req MarkdownRequest
	if err := json.Unmarshal([]byte(jsonStr), &req); err != nil {
		return errorResponse("Error: Invalid JSON payload")
	}

	var buf bytes.Buffer
	if err := goldmark.Convert([]byte(req.Markdown), &buf); err != nil {
		return errorResponse("Error: Failed to parse markdown")
	}

	resBytes := buf.Bytes()
	resPtr, resSize := bytesToPtr(resBytes)

	allocMutex.Lock()
	allocs[resPtr] = resBytes
	allocMutex.Unlock()

	return uint64(resSize)<<32 | uint64(resPtr)
}

func errorResponse(msg string) uint64 {
	b := []byte("<p>" + msg + "</p>")
	p, s := bytesToPtr(b)
	allocMutex.Lock()
	allocs[p] = b
	allocMutex.Unlock()
	return uint64(s)<<32 | uint64(p)
}

// --- SHARED WASM INTEROP MEMORY FUNCTIONS ---

//go:wasmexport free
func free(ptr uint32) {
	allocMutex.Lock()
	delete(allocs, ptr)
	allocMutex.Unlock()
}

//go:noinline
func getString(ptr, size uint32) string {
	return string((*[1 << 30]byte)(unsafe.Pointer(uintptr(ptr)))[:size:size])
}

//go:noinline
func bytesToPtr(b []byte) (uint32, uint32) {
	if len(b) == 0 {
		return 0, 0
	}
	// FIX: Reference the first data element, NOT the slice header variable
	return uint32(uintptr(unsafe.Pointer(&b[0]))), uint32(len(b))
}

//go:wasmexport malloc
func malloc(size uint32) uintptr {
	// If size is 0, allocate at least 1 byte to prevent an index out of bounds error
	if size == 0 {
		size = 1
	}
	buf := make([]byte, size)

	// FIX: Point directly to the first element in the backing array data space
	ptr := uintptr(unsafe.Pointer(&buf[0]))

	allocMutex.Lock()
	allocs[uint32(ptr)] = buf
	allocMutex.Unlock()

	return ptr
}
