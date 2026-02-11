package com.panda.sideshelf

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * Manages persistent storage of clipboard items using SharedPreferences.
 * Thread-safe operations with 50-item limit and 7-day expiration.
 */
class ClipboardStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val items = mutableListOf<ShelfItem>()

    companion object {
        private const val PREFS_NAME = "clipboard_storage"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 50
        private val EXPIRATION_MILLIS = TimeUnit.DAYS.toMillis(7)
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
        // Add to beginning (most recent first)
        items.add(0, item)
        
        // Enforce max items limit
        if (items.size > MAX_ITEMS) {
            items.removeAt(items.size - 1)
        }
        
        saveItems()
    }

    /**
     * Remove an item from storage
     */
    @Synchronized
    fun removeItem(item: ShelfItem) {
        items.remove(item)
        saveItems()
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
        val beforeSize = items.size
        items.removeAll { (now - it.timestamp) > EXPIRATION_MILLIS }
        if (items.size != beforeSize) {
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
            is ShelfItem.ImageItem -> uri != null && lastItem.uri == uri
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
        val uri: String? = null
    ) {
        fun toShelfItem(): ShelfItem {
            return when (type) {
                "text" -> ShelfItem.TextItem(id, text ?: "", timestamp)
                "image" -> ShelfItem.ImageItem(id, uri ?: "", timestamp)
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }

        companion object {
            fun fromShelfItem(item: ShelfItem): StoredItem {
                return when (item) {
                    is ShelfItem.TextItem -> StoredItem("text", item.id, item.timestamp, text = item.text)
                    is ShelfItem.ImageItem -> StoredItem("image", item.id, item.timestamp, uri = item.uri)
                }
            }
        }
    }
}
