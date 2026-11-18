package dev.mazzagotte.strike3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.mazzagotte.strike3.ui.AppRoot
import dev.mazzagotte.strike3.ui.Strike3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Strike3Theme {
                AppRoot()
            }
        }
    }
}
