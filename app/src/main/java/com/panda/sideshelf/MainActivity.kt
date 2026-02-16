package com.panda.sideshelf

import android.os.Bundle
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.panda.sideshelf.ui.theme.SideShelfTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleShareIntent(intent)

        setContent {
            SideShelfTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartService = { startOverlayService() },
                        checkOverlayPermission = { Settings.canDrawOverlays(this) },
                        requestOverlayPermission = { requestOverlayPermission() },
                        checkAccessibilityPermission = { isAccessibilityServiceEnabled() },
                        requestAccessibilityPermission = { requestAccessibilityPermission() }
                    )
                }
            }
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                val storage = ClipboardStorage(this)
                val item = ShelfItem.ImageItem(
                    id = System.currentTimeMillis(),
                    uri = uri.toString(),
                    timestamp = System.currentTimeMillis()
                )
                storage.addItem(item)
                android.widget.Toast.makeText(this, "Image added to SideShelf", android.widget.Toast.LENGTH_SHORT).show()
                
                // Optional: Close the activity if it was only opened for sharing
                // finish() 
            }
        }
    }

    private fun startOverlayService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val output = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return output?.contains("$packageName/${SidebarAccessibilityService::class.java.name}") == true
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartService: () -> Unit,
    checkOverlayPermission: () -> Boolean,
    requestOverlayPermission: () -> Unit,
    checkAccessibilityPermission: () -> Boolean,
    requestAccessibilityPermission: () -> Unit
) {
    // We use a simple way to refresh state on resume for demo purposes
    // In production, consider LifecycleEventObserver
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission()) }
    var hasAccessibilityPermission by remember { mutableStateOf(checkAccessibilityPermission()) }

    // This is a bit hacky for Compose, usually you'd use a LifecycleEventObserver
    // But for simplicity in this artifact we'll just rely on the user clicking buttons to refresh or re-entering
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasOverlayPermission && hasAccessibilityPermission) {
            Text(text = "All permissions granted!")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStartService) {
                Text(text = "Start SideShelf")
            }
        } else {
            Text(text = "SideShelf requires permissions:")
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!hasOverlayPermission) {
                 Button(onClick = {
                    requestOverlayPermission()
                    // Optimistically update or wait for resume
                }) {
                    Text(text = "Grant Overlay Permission")
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(text = "✅ Overlay Permission Granted")
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!hasAccessibilityPermission) {
                 Button(onClick = {
                    requestAccessibilityPermission()
                }) {
                    Text(text = "Grant Accessibility (Clipboard)")
                }
                 Text(
                    text = "Required for background clipboard monitoring",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            } else {
                Text(text = "✅ Accessibility Permission Granted")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            hasOverlayPermission = checkOverlayPermission()
            hasAccessibilityPermission = checkAccessibilityPermission()
        }) {
            Text("Refresh Permissions Check")
        }
    }
}