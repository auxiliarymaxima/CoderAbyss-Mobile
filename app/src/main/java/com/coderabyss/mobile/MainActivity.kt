package com.coderabyss.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.coderabyss.mobile.platformui.PlatformApp

/** Navigation observes durable repositories; the Activity never owns an inference job. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00CFFF),
                background = Color(0xFF020810), surface = Color(0xFF061522))) { PlatformApp() }
        }
    }
}
