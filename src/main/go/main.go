package main

import (
	"github.com/common-nighthawk/go-figure"
)

func main() {
	myFigure := figure.NewFigure("Hello from Go using WASM!", "", true)
	myFigure.Print()
}
