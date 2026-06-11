package main

import (
	"encoding/json"
	"strings"
	"sync"
	"unsafe"

	"github.com/common-nighthawk/go-figure"
)

type ArtRequest struct {
	Text string `json:"text"`
	Font string `json:"font"` // e.g., "slant", "standard", "shadow"
}

var (
	allocMutex sync.Mutex
	allocs     = make(map[uint32][]byte)
)

func main() {}

//go:wasmexport GetASCIIArt
func GetASCIIArt(ptr, size uint32) uint64 {
	// 1. Read JSON configuration string from memory
	jsonStr := getString(ptr, size)

	var req ArtRequest
	if err := json.Unmarshal([]byte(jsonStr), &req); err != nil {
		req.Text = "Error: Invalid JSON"
		req.Font = ""
	}

	// 2. Handle Multi-line inputs natively
	lines := strings.Split(req.Text, "\n")
	var artBuilder strings.Builder

	for _, line := range lines {
		if line == "" {
			artBuilder.WriteString("\n")
			continue
		}
		// If req.Font is empty, go-figure falls back to its default font
		myFigure := figure.NewFigure(line, req.Font, true)
		artBuilder.WriteString(myFigure.String())
		artBuilder.WriteString("\n")
	}

	// 3. Return the processed output string bytes
	resBytes := []byte(artBuilder.String())
	resPtr, resSize := bytesToPtr(resBytes)

	allocMutex.Lock()
	allocs[resPtr] = resBytes
	allocMutex.Unlock()

	return uint64(resSize)<<32 | uint64(resPtr)
}

//go:wasmexport malloc
func malloc(size uint32) uintptr {
	buf := make([]byte, size)
	ptr := uintptr(unsafe.Pointer(&buf[0]))

	allocMutex.Lock()
	allocs[uint32(ptr)] = buf
	allocMutex.Unlock()

	return ptr
}

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
	return uint32(uintptr(unsafe.Pointer(&b[0]))), uint32(len(b))
}
