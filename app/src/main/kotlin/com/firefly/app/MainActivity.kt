package com.firefly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.firefly.app.ui.AppRoot
import com.firefly.app.ui.theme.FireflyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FireflyApp).container
        setContent {
            FireflyTheme {
                AppRoot(container)
            }
        }
    }
}
