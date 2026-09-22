package com.coderabyss.mobile

import kotlinx.coroutines.sync.Mutex

/** One memory-heavy native inference operation at a time, including speech and video. */
object LocalInferenceGate { val mutex = Mutex() }
