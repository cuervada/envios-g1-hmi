package edu.uade.enviosg1

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import edu.uade.enviosg1.ui.EnviosG1App
import edu.uade.enviosg1.ui.theme.EnviosG1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.BLACK
            )
        )
        setContent {
            EnviosG1Theme {
                EnviosG1App()
            }
        }
    }
}
