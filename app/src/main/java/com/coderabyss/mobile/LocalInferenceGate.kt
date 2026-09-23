package com.coderabyss.mobile

import kotlinx.coroutines.sync.Mutex

/** One local text LLM at a time. Speech owns a separate mutex. */
object LocalInferenceGate { val mutex = Mutex() }
