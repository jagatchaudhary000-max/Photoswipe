package com.jagat.photoswipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jagat.photoswipe.ui.App
import com.jagat.photoswipe.ui.PhotoSwipeTheme

class MainActivity : ComponentActivity() {
    private val vm: SwipeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSwipeTheme {
                App(vm)
            }
        }
    }
}
