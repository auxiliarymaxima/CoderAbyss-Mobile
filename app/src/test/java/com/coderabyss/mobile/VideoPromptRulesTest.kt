package com.coderabyss.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPromptRulesTest {
    @Test fun whitespaceDoesNotAddWords() {
        assertEquals(0, VideoPromptRules.words(" \n\t\u00a0 "))
        assertEquals(3, VideoPromptRules.words("  red\tboat\n\u00a0sailing  "))
    }
    @Test fun acceptsExactlyOneHundredWords() {
        VideoPromptRules.validateWan(List(100) { "boat" }.joinToString(" \n"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOneHundredOneWords() {
        VideoPromptRules.validateWan(List(101) { "boat" }.joinToString(" "))
    }
    @Test fun validationPreservesEditablePrompt() {
        val prompt = List(101) { "boat" }.joinToString("  ")
        runCatching { VideoPromptRules.validateWan(prompt) }
        assertEquals(101, VideoPromptRules.words(prompt))
        assertEquals(List(101) { "boat" }.joinToString("  "), prompt)
    }
}
