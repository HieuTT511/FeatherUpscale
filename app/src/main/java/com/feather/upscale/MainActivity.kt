package com.feather.upscale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.feather.upscale.ui.UpscaleScreen
import com.feather.upscale.ui.UpscaleViewModel
import com.feather.upscale.ui.theme.FeatherUpscaleTheme

class MainActivity : ComponentActivity() {

    private val viewModel: UpscaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeatherUpscaleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UpscaleScreen(viewModel = viewModel)
                }
            }
        }
    }
}
