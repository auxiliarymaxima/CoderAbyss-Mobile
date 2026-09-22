package com.coderabyss.mobile

object VideoPromptRules {
    const val WAN_MAX_WORDS = 100
    fun words(prompt: String): Int = prompt.split(Regex("[\\s\\p{Z}]+")).count { it.isNotEmpty() }
    fun validateWan(prompt: String) {
        require(prompt.isNotBlank()) { "Enter a video prompt." }
        require(words(prompt) <= WAN_MAX_WORDS) {
            "Wan video prompts are limited to 100 words for better generation quality. Shorten your prompt."
        }
    }
}
