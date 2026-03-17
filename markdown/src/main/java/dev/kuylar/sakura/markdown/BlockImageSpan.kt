package dev.kuylar.sakura.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withTranslation
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.roundToInt

class BlockImageSpan(
    private val imageUrl: String,
    @param:ColorInt private val backgroundColor: Int = 0x22000000,
    private val maxWidthPx: (() -> Int)? = null,
    private val fallbackHeightPx: Int = 160,
    private val glide: RequestManager? = null
) : ReplacementSpan() {
    var onImageLoaded: (() -> Unit)? = null
    private var isLoadingStarted = false

    private var drawable: Drawable = backgroundColor.toDrawable().apply {
        setBounds(0, 0, 1, fallbackHeightPx)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        ensureLoading()

        val bounds = drawable.bounds
        if (fm != null) {
            fm.ascent = -bounds.height()
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = 0
        }

        return bounds.width().coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        ensureLoading()

        canvas.withTranslation(x, top.toFloat()) {
			drawable.draw(this)
		}
    }

    private fun ensureLoading() {
        if (isLoadingStarted) return

        Log.i("BlockImageSpan", "loading image: $imageUrl")
        val targetWidth = (maxWidthPx?.invoke() ?: fallbackHeightPx).coerceAtLeast(0)
        Log.i("BlockImageSpan", "width: $targetWidth")
        if (targetWidth <= 0) return

        isLoadingStarted = true

        Log.i("BlockImageSpan", "load start")
        glide
            ?.load(imageUrl)
            ?.into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    Log.i("BlockImageSpan", "load end")
                    val intrinsicWidth = resource.intrinsicWidth.takeIf { it > 0 } ?: targetWidth
                    val intrinsicHeight = resource.intrinsicHeight.takeIf { it > 0 } ?: fallbackHeightPx

                    val scaledHeight =
                        (intrinsicHeight * (targetWidth / intrinsicWidth.toFloat())).roundToInt()

                    resource.setBounds(0, 0, targetWidth, scaledHeight)
                    drawable = resource
                    onImageLoaded?.invoke()
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }
}
