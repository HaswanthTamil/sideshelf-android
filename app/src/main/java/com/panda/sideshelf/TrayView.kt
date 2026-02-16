package com.panda.sideshelf

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TrayView(
    context: Context,
    private val storage: ClipboardStorage,
    private val onItemsChanged: () -> Unit
) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null

    private val trayContent: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor("#121212")) // Darker background
        elevation = 16f
    }

    private val recyclerView: RecyclerView
    private val emptyStateView: View
    private val adapter: ShelfAdapter
    private val trashButton: ImageView

    init {
        // Semi-transparent background for dismissal tap-outside
        setBackgroundColor(Color.parseColor("#80000000"))
        
        val trayWidth = (resources.displayMetrics.widthPixels * 0.45).toInt()
        val params = LayoutParams(trayWidth, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
        }
        
        addView(trayContent, params)

        // Setup RecyclerView with padding for bottom button
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 0, 0, (80 * resources.displayMetrics.density).toInt())
            clipToPadding = false
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // Setup empty state
        emptyStateView = LayoutInflater.from(context)
            .inflate(R.layout.empty_state, trayContent, false).apply {
                findViewById<View>(R.id.emptyText)?.let {
                    (it as? TextView)?.setTextColor(Color.GRAY)
                }
            }

        // Setup adapter
        adapter = ShelfAdapter(
            context = context,
            onItemClick = { item ->
                // Item already copied in adapter
            },
            onItemLongClick = { item ->
                showDeleteItemConfirmation(item)
            }
        )
        recyclerView.adapter = adapter

        // Setup trash button at the bottom
        trashButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.parseColor("#FF5252")) // Bright red
            background = context.getDrawable(android.R.drawable.dialog_holo_dark_frame)
            setPadding(32, 32, 32, 32)
            elevation = 18f
            setOnClickListener {
                if (storage.getItems().isNotEmpty()) {
                    showClearAllConfirmation()
                }
            }
        }

        val trashParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (32 * resources.displayMetrics.density).toInt()
        }

        // Setup swipe-to-delete with confirmation
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val items = storage.getItems()
                if (position in items.indices) {
                    val item = items[position]
                    // We need to refresh to restore the swiped item if user cancels
                    refreshItems() 
                    showDeleteItemConfirmation(item)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                val alpha = 1.0f - Math.abs(dX) / viewHolder.itemView.width.toFloat()
                viewHolder.itemView.alpha = alpha
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Add views to tray content
        trayContent.addView(recyclerView)
        trayContent.addView(emptyStateView)
        trayContent.addView(trashButton, trashParams)

        // Dismiss on tap outside
        setOnClickListener {
            animateOut()
        }

        // Prevent clicks inside tray from dismissing
        trayContent.setOnClickListener {
            // Do nothing
        }

        // Initial state for animation
        trayContent.translationX = trayWidth.toFloat()
        
        // Load and display items
        refreshItems()
        
        post {
            animateIn()
        }
    }

    private fun showClearAllConfirmation() {
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Clear Everything?")
            .setMessage("All copied text and images will be permanently deleted.")
            .setPositiveButton("Clear All") { _, _ ->
                storage.clearAll()
                refreshItems()
                onItemsChanged()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun showDeleteItemConfirmation(item: ShelfItem) {
        val type = if (item is ShelfItem.TextItem) "text" else "image"
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Delete Item?")
            .setMessage("Are you sure you want to delete this $type?")
            .setPositiveButton("Delete") { _, _ ->
                storage.removeItem(item)
                refreshItems()
                onItemsChanged()
            }
            .setNegativeButton("Cancel") { _, _ ->
                refreshItems() // Restore if it was swiped
            }
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun animateIn() {
        trayContent.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateOut() {
        trayContent.animate()
            .translationX(trayContent.width.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                onDismiss?.invoke()
            }
            .start()
    }

    /**
     * Refresh the displayed items from storage
     */
    fun refreshItems() {
        val items = storage.getItems()
        adapter.updateItems(items)
        
        // Show/hide empty state and trash button
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
            trashButton.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            trashButton.visibility = View.VISIBLE
        }
    }
}
