package com.example.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.testapp.ui.main.MainScreen
import com.example.testapp.theme.TestAppTheme
import com.example.testapp.updater.UpdateChecker

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            packageInfo.versionCode
        }
        UpdateChecker.checkForUpdate(this, currentVersionCode)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    enableEdgeToEdge()
    setContent {
      TestAppTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainScreen() } }
    }
  }
}
