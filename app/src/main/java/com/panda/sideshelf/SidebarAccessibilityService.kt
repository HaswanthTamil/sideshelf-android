package com.panda.sideshelf

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class SidebarAccessibilityService : AccessibilityService() {

    private var clipboardListener: ClipboardListener? = null
    private lateinit var clipboardStorage: ClipboardStorage

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Initialize storage
        clipboardStorage = ClipboardStorage(this)
        
        // Initialize clipboard listener
        clipboardListener = ClipboardListener(this) { newItem ->
            // Check for duplicates before adding
            val isDuplicate = when (newItem) {
                is ShelfItem.TextItem -> clipboardStorage.isLastItemDuplicate(text = newItem.text)
                is ShelfItem.ImageItem -> clipboardStorage.isLastItemDuplicate(uri = newItem.uri)
            }
            
            if (!isDuplicate) {
                clipboardStorage.addItem(newItem)
                
                // Debug: Show toast when item is added
                if (newItem is ShelfItem.TextItem) {
                    Toast.makeText(this, "SideShelf: Copied ${newItem.text.take(20)}...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SideShelf: Image copied", Toast.LENGTH_SHORT).show()
                }
                
                // We don't have direct access to trayView here, but storage is shared via SharedPreferences
                // The overlay service will refresh when opened next time, or we could broadcast if needed
            }
        }
        clipboardListener?.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Check clipboard on any relevant event as a backup to the listener
        clipboardListener?.checkClipboard()
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardListener?.stop()
    }
}
