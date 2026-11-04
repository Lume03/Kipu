package com.kipu.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Soft cross-fade between the light and the dark theme.
 *
 * The theme flips as one change of the whole color scheme, so instead of animating every color the current
 * window is frozen as a picture over the UI, the theme changes underneath it and the picture fades out.
 * It is only used for a change the owner makes by hand; a theme that follows the system, or a device with
 * animations reduced, switches instantly as before.
 */
object ThemeTransition {
    private const val FADE_MILLIS = 320L
    /** If the new theme never arrives (the choice did not change the look), the picture still goes away. */
    private const val SAFETY_MILLIS = 1_200L

    private val main = Handler(Looper.getMainLooper())
    private var overlay: ImageView? = null
    private var safety: Runnable? = null

    /**
     * Freezes the window and then runs [change], which must flip the theme. Anything that stops the picture
     * (reduced motion, old Android, no window yet) just runs [change] straight away.
     */
    fun run(context: Context, reducedMotion: Boolean, change: () -> Unit) {
        val activity = context.findActivity()
        if (reducedMotion || activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return change()
        val decor = activity.window.decorView
        if (!decor.isAttachedToWindow || decor.width == 0 || decor.height == 0) return change()
        clear()
        val bitmap = runCatching { Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: return change()
        try {
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) show(activity, bitmap) else bitmap.recycle()
                change()
            }, main)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            change()
        }
    }

    /** The new theme is composed: wait for its first frames to draw, then fade the frozen picture out. */
    fun onThemeApplied() {
        val frozen = overlay ?: return
        frozen.postOnAnimation { frozen.postOnAnimation { fade(frozen) } }
    }

    private fun show(activity: Activity, bitmap: Bitmap) {
        val frozen = ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        (activity.window.decorView as ViewGroup).addView(frozen)
        overlay = frozen
        safety = Runnable { overlay?.let(::fade) }.also { main.postDelayed(it, SAFETY_MILLIS) }
    }

    private fun fade(frozen: ImageView) {
        if (overlay !== frozen) return
        safety?.let(main::removeCallbacks)
        safety = null
        frozen.animate().cancel()
        frozen.animate().alpha(0f).setDuration(FADE_MILLIS)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction { if (overlay === frozen) clear() }
            .start()
    }

    private fun clear() {
        safety?.let(main::removeCallbacks)
        safety = null
        overlay?.let { view ->
            view.animate().cancel()
            (view.parent as? ViewGroup)?.removeView(view)
            view.setImageDrawable(null)
        }
        overlay = null
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
