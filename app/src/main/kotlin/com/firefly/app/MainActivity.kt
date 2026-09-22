package com.firefly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.firefly.app.ui.home.HomeScreen
import com.firefly.app.ui.theme.FireflyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FireflyTheme {
                HomeScreen()
            }
        }
    }
}
