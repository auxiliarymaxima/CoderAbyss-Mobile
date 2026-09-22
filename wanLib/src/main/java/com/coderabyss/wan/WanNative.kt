package com.coderabyss.wan

object WanNative {
    init { System.loadLibrary("coder-wan") }
    external fun reset()
    external fun cancel()
    external fun status(): String
    external fun generate(directory: String, prompt: String, output: String, width: Int, height: Int, frames: Int, steps: Int): Int
}
