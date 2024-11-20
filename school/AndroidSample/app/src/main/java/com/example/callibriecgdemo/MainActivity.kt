package com.example.callibriecgdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContentProviderCompat.requireContext
import com.example.callibriecgdemo.ui.theme.CallibriECGDemoTheme
import com.neurosdk2.helpers.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
                    CallibriECGDemoTheme {
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
                CallibriECGDemoTheme {
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
