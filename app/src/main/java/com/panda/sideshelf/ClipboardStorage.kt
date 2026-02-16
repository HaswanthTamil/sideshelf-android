package com.panda.sideshelf

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages persistent storage of clipboard items using SharedPreferences.
 * Thread-safe operations with 50-item limit and 7-day expiration.
 */
class ClipboardStorage(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val items = mutableListOf<ShelfItem>()

    companion object {
        private const val PREFS_NAME = "clipboard_storage"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 50
        private val EXPIRATION_MILLIS = TimeUnit.DAYS.toMillis(7)
        private const val IMAGE_DIR = "clipboard_images"
    }

    init {
        loadItems()
        cleanupExpiredItems()
    }

    /**
     * Load items from SharedPreferences
     */
    @Synchronized
    private fun loadItems() {
        val json = prefs.getString(KEY_ITEMS, null) ?: return
        try {
            val type = object : TypeToken<List<StoredItem>>() {}.type
            val storedItems: List<StoredItem> = gson.fromJson(json, type)
            items.clear()
            items.addAll(storedItems.map { it.toShelfItem() })
        } catch (e: Exception) {
            e.printStackTrace()
            items.clear()
        }
    }

    /**
     * Save items to SharedPreferences
     */
    @Synchronized
    private fun saveItems() {
        val storedItems = items.map { StoredItem.fromShelfItem(it) }
        val json = gson.toJson(storedItems)
        prefs.edit().putString(KEY_ITEMS, json).apply()
    }

    /**
     * Add a new item to storage
     */
    @Synchronized
    fun addItem(item: ShelfItem) {
        var finalItem = item
        
        // Deduplication based on content or source URI
        // Find existing match first to avoid mutation inside the closure
        val existingMatch = items.find { existing ->
            when (item) {
                is ShelfItem.TextItem -> existing is ShelfItem.TextItem && existing.text == item.text
                is ShelfItem.ImageItem -> {
                    if (existing is ShelfItem.ImageItem) {
                        (item.sourceUri != null && existing.sourceUri == item.sourceUri) ||
                        (existing.uri == item.uri)
                    } else false
                }
            }
        }

        if (existingMatch != null) {
            if (item is ShelfItem.ImageItem && existingMatch is ShelfItem.ImageItem) {
                // Reuse existing local file info
                finalItem = item.copy(uri = existingMatch.uri, sourceUri = existingMatch.sourceUri)
            }
            items.remove(existingMatch)
        }

        // If it's an image and still needs capturing (not a local file yet)
        if (finalItem is ShelfItem.ImageItem) {
            val imageItem = finalItem
            if (!imageItem.uri.startsWith("file://")) {
                val localUri = captureImageLocally(imageItem.uri)
                if (localUri != null) {
                    val sourceUri = (item as? ShelfItem.ImageItem)?.uri ?: imageItem.uri
                    finalItem = imageItem.copy(uri = localUri, sourceUri = sourceUri)
                }
            }
        }

        // Add to beginning (most recent first)
        items.add(0, finalItem)
        
        // Remove items older than 7 days
        cleanupExpiredItems()

        // Enforce max items limit
        while (items.size > MAX_ITEMS) {
            val last = items.removeAt(items.size - 1)
            if (last is ShelfItem.ImageItem) {
                deleteImageFile(last.uri)
            }
        }
        
        saveItems()
    }

    /**
     * Remove an item from storage
     */
    @Synchronized
    fun removeItem(item: ShelfItem) {
        if (items.remove(item)) {
            if (item is ShelfItem.ImageItem) {
                deleteImageFile(item.uri)
            }
            saveItems()
        }
    }

    /**
     * Get all items
     */
    @Synchronized
    fun getItems(): List<ShelfItem> {
        return items.toList()
    }

    /**
     * Remove items older than 7 days
     */
    @Synchronized
    fun cleanupExpiredItems() {
        val now = System.currentTimeMillis()
        val expired = items.filter { (now - it.timestamp) > EXPIRATION_MILLIS }
        
        if (expired.isNotEmpty()) {
            expired.forEach { item ->
                if (item is ShelfItem.ImageItem) {
                    deleteImageFile(item.uri)
                }
            }
            items.removeAll(expired)
            saveItems()
        }
    }

    /**
     * Check if the last item matches the given content (to prevent duplicates)
     */
    @Synchronized
    fun isLastItemDuplicate(text: String? = null, uri: String? = null): Boolean {
        if (items.isEmpty()) return false
        
        return when (val lastItem = items.first()) {
            is ShelfItem.TextItem -> text != null && lastItem.text == text
            is ShelfItem.ImageItem -> {
                if (uri == null) false
                else lastItem.uri == uri || lastItem.sourceUri == uri
            }
        }
    }

    private fun captureImageLocally(uriString: String): String? {
        return try {
            val sourceUri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            
            val dir = File(context.filesDir, IMAGE_DIR)
            if (!dir.exists()) dir.mkdirs()
            
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val destFile = File(dir, fileName)
            
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deleteImageFile(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    // Ensure we ONLY delete from our own directory
                    if (file.exists() && file.absolutePath.contains(IMAGE_DIR)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Internal storage class for JSON serialization
     */
    private data class StoredItem(
        val type: String,
        val id: Long,
        val timestamp: Long,
        val text: String? = null,
        val uri: String? = null,
        val sourceUri: String? = null
    ) {
        fun toShelfItem(): ShelfItem {
            return when (type) {
                "text" -> ShelfItem.TextItem(id, text ?: "", timestamp)
                "image" -> ShelfItem.ImageItem(id, uri ?: "", sourceUri, timestamp)
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }

        companion object {
            fun fromShelfItem(item: ShelfItem): StoredItem {
                return when (item) {
                    is ShelfItem.TextItem -> StoredItem("text", item.id, item.timestamp, text = item.text)
                    is ShelfItem.ImageItem -> StoredItem("image", item.id, item.timestamp, uri = item.uri, sourceUri = item.sourceUri)
                }
            }
        }
    }
}
