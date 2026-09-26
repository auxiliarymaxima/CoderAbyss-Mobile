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
        assertEquals("com.coderabyss.mobile", context.packageName)
        assertEquals("1:342807159631:android:47964321c134b76bd59b3a", options.applicationId)
        assertFalse(options.apiKey.isBlank())
        // Public Web client ID from the registered app, never the Android OAuth client ID.
        assertEquals("342807159631-be34s9osrjomhvbv67mcgk7b1us6jcam.apps.googleusercontent.com", context.getString(R.string.default_web_client_id))
    }
}
