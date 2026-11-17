package com.lyricglow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyricGlowHome() }
    }
}

@Composable
private fun LyricGlowHome() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LyricGlow", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Ad-free music, your way.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Text("Your library is waiting", style = MaterialTheme.typography.titleMedium)
                Text("Native playback and provider connections are the next build steps.")
            }
        }
    }
}
