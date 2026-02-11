package com.panda.sideshelf

import android.os.Handler
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper

/**
 * Monitors clipboard changes and notifies when new items are copied.
 * Prevents duplicate consecutive entries.
 */
class ClipboardListener(
    private val context: Context,
    private val onNewItem: (ShelfItem) -> Unit
) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastClipText: String? = null
    private var lastClipUri: String? = null
    
    // Polling mechanism for reliability
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            checkClipboard()
            mainHandler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    /**
     * Start listening to clipboard changes
     */
    fun start() {
        clipboardManager.addPrimaryClipChangedListener(listener)
        mainHandler.post(pollRunnable)
    }

    /**
     * Stop listening to clipboard changes
     */
    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(listener)
        mainHandler.removeCallbacks(pollRunnable)
    }

    /**
     * Manually check clipboard for new content
     */
    fun checkClipboard() {
        handleClipboardChange()
    }

    private fun handleClipboardChange() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        
        // Check for text
        val text = item.text?.toString()
        if (!text.isNullOrBlank()) {
            // Avoid duplicate consecutive text
            if (text != lastClipText) {
                lastClipText = text
                lastClipUri = null
                
                val shelfItem = ShelfItem.TextItem(
                    id = System.currentTimeMillis(),
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                onNewItem(shelfItem)
            }
            return
        }

        // Check for URI (image)
        val uri = item.uri
        if (uri != null) {
            val uriString = uri.toString()
            // Avoid duplicate consecutive URIs
            if (uriString != lastClipUri) {
                lastClipUri = uriString
                lastClipText = null
                
                val shelfItem = ShelfItem.ImageItem(
                    id = System.currentTimeMillis(),
                    uri = uriString,
                    timestamp = System.currentTimeMillis()
                )
                onNewItem(shelfItem)
            }
        }
    }
}
