package com.audioninja.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView

/**
 * A single satellite button in the bubble's arc menu — just your uploaded image,
 * positioned as its own tiny overlay window so it can sit outside the main
 * bubble's bounds and be tapped independently.
 */
class BubbleArcButton(
    private val context: Context,
    private val windowManager: WindowManager,
    drawableName: String,
    private val sizePx: Int = 120
) {
    private var view: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private val resId: Int = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

    fun show(centerX: Int, centerY: Int, onClick: () -> Unit) {
        if (view != null) {
            move(centerX, centerY)
            return
        }
        val imageView = ImageView(context).apply {
            if (resId != 0) setImageResource(resId)
            setOnClickListener { onClick() }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = centerX - sizePx / 2
            y = centerY - sizePx / 2
        }

        try {
            windowManager.addView(imageView, p)
            view = imageView
            params = p
        } catch (_: Exception) { }
    }

    fun move(centerX: Int, centerY: Int) {
        val v = view ?: return
        val p = params ?: return
        p.x = centerX - sizePx / 2
        p.y = centerY - sizePx / 2
        try { windowManager.updateViewLayout(v, p) } catch (_: Exception) { }
    }

    fun hide() {
        view?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        view = null
        params = null
    }
}
