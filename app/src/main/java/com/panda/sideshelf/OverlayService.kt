package com.panda.sideshelf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var handleView: HandleView? = null
    private var trayView: TrayView? = null
    
    private lateinit var clipboardStorage: ClipboardStorage
    private var clipboardListener: ClipboardListener? = null

    companion object {
        private const val CHANNEL_ID = "OverlayServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize clipboard storage
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
                // Refresh tray if it's open
                trayView?.refreshItems()
            }
        }
        clipboardListener?.start()
        
        setupHandle()
    }

    private fun setupHandle() {
        // Define handle height (e.g., 80dp)
        val handleHeight = (80 * resources.displayMetrics.density).toInt()
        
        handleView = HandleView(this).apply {
            // We'll handle clicks manually via touch listener to distinguish from drags
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            handleHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END // Use TOP to control Y strictly
            x = 0
            y = (resources.displayMetrics.heightPixels - handleHeight) / 2 // Start centered
            width = (16 * resources.displayMetrics.density).toInt()
        }

        // Touch listener for dragging
        handleView?.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var initialY = 0
            private var initialTouchY = 0f
            private var startClickTime: Long = 0

            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
                        initialTouchY = event.rawY
                        startClickTime = System.currentTimeMillis()
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.y = initialY + dy
                        windowManager.updateViewLayout(handleView, params)
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - startClickTime
                        val dy = (event.rawY - initialTouchY).toInt()
                        // If moved less than 10 pixels and short duration, treat as click
                        if (Math.abs(dy) < 10 && clickDuration < 200) {
                            showTray()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(handleView, params)
    }

    private fun showTray() {
        if (trayView == null) {
            trayView = TrayView(
                context = this,
                storage = clipboardStorage,
                onItemsChanged = {
                    // Cleanup expired items when items change
                    clipboardStorage.cleanupExpiredItems()
                }
            ).apply {
                onDismiss = {
                    hideTray()
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(trayView, params)
            handleView?.visibility = android.view.View.GONE
        }
    }

    private fun hideTray() {
        trayView?.let {
            windowManager.removeView(it)
            trayView = null
            handleView?.visibility = android.view.View.VISIBLE
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardListener?.stop()
        handleView?.let { windowManager.removeView(it) }
        trayView?.let { windowManager.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SideShelf Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SideShelf is active")
            .setContentText("Tap the handle on the right to open.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // Use a system icon for now
            .build()
    }
}
