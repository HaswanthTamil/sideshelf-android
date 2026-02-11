package com.panda.sideshelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
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
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        elevation = 16f
    }

    private val recyclerView: RecyclerView
    private val emptyStateView: View
    private val adapter: ShelfAdapter

    init {
        // Semi-transparent background for dismissal tap-outside
        setBackgroundColor(Color.parseColor("#40000000"))
        
        val trayWidth = (resources.displayMetrics.widthPixels * 0.4).toInt()
        val params = LayoutParams(trayWidth, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
        }
        
        addView(trayContent, params)

        // Setup RecyclerView
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // Setup empty state
        emptyStateView = LayoutInflater.from(context)
            .inflate(R.layout.empty_state, trayContent, false)

        // Setup adapter
        adapter = ShelfAdapter(
            context = context,
            onItemClick = { item ->
                // Item already copied in adapter
            },
            onItemLongClick = { item ->
                deleteItem(item)
            }
        )
        recyclerView.adapter = adapter

        // Setup swipe-to-delete
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
                    deleteItem(items[position])
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
                
                // Optional: Add visual feedback during swipe
                val alpha = 1.0f - Math.abs(dX) / viewHolder.itemView.width.toFloat()
                viewHolder.itemView.alpha = alpha
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Add views to tray content
        trayContent.addView(recyclerView)
        trayContent.addView(emptyStateView)

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
        
        // Show/hide empty state
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
        }
    }

    /**
     * Delete an item from storage and refresh
     */
    private fun deleteItem(item: ShelfItem) {
        storage.removeItem(item)
        refreshItems()
        onItemsChanged()
    }
}
