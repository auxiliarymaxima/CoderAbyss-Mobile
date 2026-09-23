package com.coderabyss.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.coderabyss.mobile.presentation.AbyssTheme
import com.coderabyss.mobile.presentation.CoderAbyssShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AbyssTheme { CoderAbyssShell() } }
    }
}
