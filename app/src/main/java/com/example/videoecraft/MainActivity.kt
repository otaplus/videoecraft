package com.example.videoecraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.ui.navigation.CraftNavHost
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.theme.VideoECraftTheme

/**
 * Única Activity de VideoEcraft.
 *
 * Delega toda la navegación (Home → Editor → Export) en Compose con
 * Navigation-Compose. Mantener una sola Activity simplifica el manejo del
 * back stack y evita recrear el `ExoPlayer` del editor al navegar.
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe llamarse antes de super.onCreate() para que el splash
        // encadene con Theme.VideoECraft.Splash.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VideoECraftTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CraftVoid),
                ) {
                    CraftNavHost()
                }
            }
        }
    }
}
