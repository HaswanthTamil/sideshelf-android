package com.panda.sideshelf

/**
 * Sealed class representing items stored in the clipboard shelf.
 */
sealed class ShelfItem {
    abstract val id: Long
    abstract val timestamp: Long

    /**
     * Text clipboard item
     */
    data class TextItem(
        override val id: Long,
        val text: String,
        override val timestamp: Long
    ) : ShelfItem()

    /**
     * Image clipboard item (stores URI, not raw bitmap)
     */
    data class ImageItem(
        override val id: Long,
        val uri: String,
        override val timestamp: Long
    ) : ShelfItem()
}
