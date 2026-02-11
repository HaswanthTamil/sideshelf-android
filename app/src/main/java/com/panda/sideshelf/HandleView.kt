package com.panda.sideshelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class HandleView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    var onHandleClick: (() -> Unit)? = null

    private val rect = RectF()
    private val cornerRadius = 8f * resources.displayMetrics.density

    init {
        setOnClickListener {
            onHandleClick?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw a rounded rectangle on the right edge
        rect.set(0f, height * 0.4f, width.toFloat(), height * 0.6f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }
}
