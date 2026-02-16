package com.panda.sideshelf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.io.File
import java.util.*

/**
 * RecyclerView adapter for displaying clipboard items.
 * Supports two view types: text and image cards.
 */
class ShelfAdapter(
    private val context: Context,
    private val onItemClick: (ShelfItem) -> Unit,
    private val onItemLongClick: (ShelfItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ShelfItem>()
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_TEXT = 0
        private const val VIEW_TYPE_IMAGE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ShelfItem.TextItem -> VIEW_TYPE_TEXT
            is ShelfItem.ImageItem -> VIEW_TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_text_card, parent, false)
                TextViewHolder(view)
            }
            VIEW_TYPE_IMAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_image_card, parent, false)
                ImageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is TextViewHolder -> holder.bind(item as ShelfItem.TextItem)
            is ImageViewHolder -> holder.bind(item as ShelfItem.ImageItem)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * Update items with DiffUtil for efficient updates
     */
    fun updateItems(newItems: List<ShelfItem>) {
        val diffCallback = ShelfDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * ViewHolder for text items
     */
    inner class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContent: TextView = itemView.findViewById(R.id.textContent)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)

        fun bind(item: ShelfItem.TextItem) {
            textContent.text = item.text
            timestamp.text = dateFormat.format(Date(item.timestamp))

            itemView.setOnClickListener {
                onItemClick(item)
                copyTextToClipboard(item.text)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        private fun copyTextToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SideShelf", text)
            clipboard.setPrimaryClip(clip)
        }
    }

    /**
     * ViewHolder for image items
     */
    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageContent: ImageView = itemView.findViewById(R.id.imageContent)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)

        fun bind(item: ShelfItem.ImageItem) {
            timestamp.text = dateFormat.format(Date(item.timestamp))

            // Load image from URI
            try {
                val uri = Uri.parse(item.uri)
                imageContent.setImageURI(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                // Set placeholder or error image if needed
            }

            itemView.setOnClickListener {
                onItemClick(item)
                copyUriToClipboard(item.uri)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        private fun copyUriToClipboard(uriString: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val uri = Uri.parse(uriString)
            
            val contentUri = if (uri.scheme == "file") {
                val file = File(uri.path!!)
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                uri
            }
            
            val clip = ClipData.newUri(context.contentResolver, "SideShelf Image", contentUri)
            clipboard.setPrimaryClip(clip)
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class ShelfDiffCallback(
        private val oldList: List<ShelfItem>,
        private val newList: List<ShelfItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return when {
                oldItem is ShelfItem.TextItem && newItem is ShelfItem.TextItem ->
                    oldItem.text == newItem.text && oldItem.timestamp == newItem.timestamp
                oldItem is ShelfItem.ImageItem && newItem is ShelfItem.ImageItem ->
                    oldItem.uri == newItem.uri && oldItem.timestamp == newItem.timestamp
                else -> false
            }
        }
    }
}
