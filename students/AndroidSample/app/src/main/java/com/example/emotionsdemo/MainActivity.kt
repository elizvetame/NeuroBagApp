package com.example.emotionsdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.emotionsdemo.ui.theme.EmotionsDemoTheme
import com.neurosdk2.helpers.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
//EmotionsDemoTheme
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!PermissionHelper.HasAllPermissions(this)) {
            PermissionHelper.RequestPermissions(
                this
            ) { grantedPermissions, deniedPermissions, deniedPermanentlyPermissions ->
                setContent {
                    EmotionsDemoTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            MainScreen(
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        } else {
            setContent {
                EmotionsDemoTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Text(
                            text = "Нет разрешений!",
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}