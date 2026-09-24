package com.coderabyss.mobile

import com.google.firebase.FirebaseOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises generated resources; never fabricates a Firebase user/session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseConfigurationTest {
    @Test fun googleServicesConfiguresCorrectProjectAndWebAudience() {
        val context = RuntimeEnvironment.getApplication()
        val options = FirebaseOptions.fromResource(context)
        assertNotNull(options)
        assertEquals("coder-abyss", options!!.projectId)
        assertFalse(options.applicationId.isBlank())
        assertFalse(options.apiKey.isBlank())
        assertTrue(context.getString(R.string.default_web_client_id).endsWith(".apps.googleusercontent.com"))
    }
}
