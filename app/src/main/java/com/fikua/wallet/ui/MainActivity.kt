package com.fikua.wallet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

/** Entry point. Compose UI is a TODO — mirrors main.ts / index.html in the PWA. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Fikua Wallet — native scaffold") }
    }
}
