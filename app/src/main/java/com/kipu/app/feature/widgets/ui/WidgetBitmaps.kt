package com.kipu.app.feature.widgets.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.content.ContextCompat
import com.kipu.app.feature.widgets.data.PaceData
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the pieces a widget cannot compose with views: the app's own icons (rasterised from the same vectors the
 * screens use), bank logos, rings, sparklines and the charts. Colors are mid-tones that read on both the light
 * and the dark widget surface; bitmaps are small and cached because every widget size keeps its own RemoteViews.
 */
internal object WidgetBitmaps {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private inline fun cached(key: String, build: () -> Bitmap): Bitmap = cache.get(key) ?: build().also { cache.put(key, it) }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = style
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** A tinted rounded tile with the app's vector icon centred in it. */
    fun iconBadge(vector: ImageVector, sizePx: Int, tint: Int, background: Int, circle: Boolean = false): Bitmap =
        cached("icon:${vector.name}:$sizePx:$tint:$background:$circle") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = if (circle) sizePx / 2f else sizePx * 0.3f
            canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint(background))
            val inner = (sizePx * 0.56f).toInt()
            val offset = (sizePx - inner) / 2f
            canvas.save()
            canvas.translate(offset, offset)
            drawVector(canvas, vector, inner, tint)
            canvas.restore()
            bitmap
        }

    /** The vector as a plain black mask, tinted later with a day/night aware color filter. */
    fun glyph(vector: ImageVector, sizePx: Int): Bitmap = cached("glyph:${vector.name}:$sizePx") {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawVector(Canvas(bitmap), vector, sizePx, 0xFF000000.toInt())
        bitmap
    }

    /** A bank / wallet logo from the bundled drawable, clipped to the same rounded tile. */
    fun logo(context: Context, @DrawableRes res: Int, sizePx: Int, fullBleed: Boolean, circle: Boolean = false): Bitmap =
        cached("logo:$res:$sizePx:$fullBleed:$circle") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = if (circle) sizePx / 2f else sizePx * 0.3f
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            if (!fullBleed) canvas.drawColor(0xFFFFFFFF.toInt())
            val drawable = ContextCompat.getDrawable(context, res)
            if (drawable != null) {
                val pad = if (fullBleed) 0 else (sizePx * 0.12f).toInt()
                drawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
                drawable.draw(canvas)
            }
            bitmap
        }

    /** Round monogram, for payees and wallets without a bundled logo. */
    fun monogram(text: String, sizePx: Int, background: Int, foreground: Int): Bitmap =
        cached("mono:$text:$sizePx:$background:$foreground") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint(background))
            val textPaint = paint(foreground).apply {
                textSize = sizePx * 0.38f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText(text, sizePx / 2f, sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            bitmap
        }

    fun ring(sizePx: Int, strokePx: Float, fraction: Float, color: Int, track: Int): Bitmap =
        cached("ring:$sizePx:$strokePx:${(fraction * 200).toInt()}:$color:$track") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val inset = strokePx / 2f + 1f
            val box = RectF(inset, inset, sizePx - inset, sizePx - inset)
            canvas.drawArc(box, 0f, 360f, false, paint(track, Paint.Style.STROKE, strokePx))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) canvas.drawArc(box, -90f, max(sweep, 4f), false, paint(color, Paint.Style.STROKE, strokePx))
            bitmap
        }

    /** One arc per slice of a donut; the gaps are what keep neighbours apart on a small screen. */
    fun donut(sizePx: Int, strokePx: Float, parts: List<Pair<Float, Int>>, track: Int): Bitmap =
        cached("donut:$sizePx:$strokePx:${parts.joinToString { "${(it.first * 1000).toInt()}-${it.second}" }}:$track") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val inset = strokePx / 2f + 1f
            val box = RectF(inset, inset, sizePx - inset, sizePx - inset)
            canvas.drawArc(box, 0f, 360f, false, paint(track, Paint.Style.STROKE, strokePx))
            val total = parts.sumOf { it.first.toDouble() }.toFloat()
            if (total > 0f) {
                var start = -90f
                val gap = if (parts.count { it.first > 0f } > 1) 5f else 0f
                parts.forEach { (value, color) ->
                    val sweep = value / total * 360f
                    if (value > 0f) canvas.drawArc(box, start + gap / 2f, max(sweep - gap, 2f), false, paint(color, Paint.Style.STROKE, strokePx))
                    start += sweep
                }
            }
            bitmap
        }

    fun sparkline(widthPx: Int, heightPx: Int, values: List<Long>, color: Int, fillTop: Int): Bitmap =
        cached("spark:$widthPx:$heightPx:${values.hashCode()}:$color:$fillTop") {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (values.size >= 2) {
                val stroke = heightPx * 0.07f
                val pad = stroke * 2f
                val lo = values.min().toFloat()
                val hi = values.max().toFloat()
                val span = max(hi - lo, 1f)
                val points = values.mapIndexed { i, v ->
                    pad + (widthPx - 2 * pad) * i / (values.size - 1) to (heightPx - pad) - (heightPx - 2 * pad) * (v - lo) / span
                }
                val line = Path().apply {
                    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                }
                val area = Path(line).apply {
                    lineTo(points.last().first, heightPx.toFloat()); lineTo(points.first().first, heightPx.toFloat()); close()
                }
                canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), fillTop, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                })
                canvas.drawPath(line, paint(color, Paint.Style.STROKE, stroke))
                val (ex, ey) = points.last()
                canvas.drawCircle(ex, ey, stroke * 1.7f, paint(color))
            }
            bitmap
        }

    /** The weekly contributions as knots on a cord, in the spirit of the kipu: filled knots are weeks that counted. */
    fun cord(widthPx: Int, heightPx: Int, weeks: List<Boolean>, color: Int, line: Int, missed: Int): Bitmap =
        cached("cord:$widthPx:$heightPx:${weeks.joinToString("") { if (it) "1" else "0" }}:$color:$line:$missed") {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cy = heightPx / 2f
            val r = heightPx * 0.30f
            canvas.drawLine(r, cy, widthPx - r, cy, paint(line, Paint.Style.STROKE, heightPx * 0.07f))
            weeks.forEachIndexed { i, done ->
                val cx = r + (widthPx - 2 * r) * i / max(weeks.size - 1, 1)
                if (done) {
                    canvas.drawCircle(cx, cy, r, paint(color))
                } else {
                    canvas.drawCircle(cx, cy, r * 0.8f, paint(missed, Paint.Style.STROKE, heightPx * 0.07f))
                }
            }
            bitmap
        }

    /** Spending so far against the reference line, with where the month ends if the pace holds. */
    fun pace(widthPx: Int, heightPx: Int, pace: PaceData, color: Int, ideal: Int, grid: Int, warn: Int): Bitmap =
        cached("pace:$widthPx:$heightPx:${pace.cumulative.hashCode()}:${pace.reference?.minorUnits}:$color:$ideal:$grid:$warn") {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val stroke = heightPx * 0.028f
            val padX = stroke * 3f
            val padTop = stroke * 3f
            val padBottom = stroke * 3f
            val days = pace.daysInMonth
            val actualEnd = pace.cumulative.lastOrNull() ?: 0L
            val projected = if (pace.dayOfMonth > 0) actualEnd * days / pace.dayOfMonth else actualEnd
            val top = max(max(actualEnd, projected), pace.reference?.minorUnits ?: 0L).coerceAtLeast(1L).toFloat() * 1.08f
            fun x(day: Float) = padX + (widthPx - 2 * padX) * day / days
            fun y(v: Float) = (heightPx - padBottom) - (heightPx - padTop - padBottom) * v / top
            canvas.drawLine(padX, y(0f), widthPx - padX, y(0f), paint(grid, Paint.Style.STROKE, stroke * 0.6f))
            pace.reference?.let { reference ->
                val dashed = paint(ideal, Paint.Style.STROKE, stroke).apply { pathEffect = DashPathEffect(floatArrayOf(stroke * 3, stroke * 3), 0f) }
                canvas.drawLine(x(0f), y(0f), x(days.toFloat()), y(reference.minorUnits.toFloat()), dashed)
                if (pace.referenceIsBudget) canvas.drawLine(padX, y(reference.minorUnits.toFloat()), widthPx - padX, y(reference.minorUnits.toFloat()), paint(grid, Paint.Style.STROKE, stroke * 0.6f))
            }
            if (pace.cumulative.isNotEmpty()) {
                val line = Path().apply {
                    moveTo(x(0f), y(0f))
                    pace.cumulative.forEachIndexed { i, v -> lineTo(x((i + 1).toFloat()), y(v.toFloat())) }
                }
                val area = Path(line).apply { lineTo(x(pace.cumulative.size.toFloat()), y(0f)); lineTo(x(0f), y(0f)); close() }
                canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, padTop, 0f, y(0f), (color and 0x00FFFFFF) or 0x55000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                })
                canvas.drawPath(line, paint(color, Paint.Style.STROKE, stroke * 1.4f))
                val ex = x(pace.cumulative.size.toFloat())
                val ey = y(actualEnd.toFloat())
                val over = pace.idealToday?.let { actualEnd > it.minorUnits } == true
                val dot = if (over) warn else color
                canvas.drawLine(ex, ey, x(days.toFloat()), y(projected.toFloat()), paint(dot, Paint.Style.STROKE, stroke).apply {
                    pathEffect = DashPathEffect(floatArrayOf(stroke * 0.1f, stroke * 2.6f), 0f)
                })
                canvas.drawCircle(ex, ey, stroke * 2.4f, paint(dot))
            }
            bitmap
        }

    // ---- ImageVector rasteriser -------------------------------------------------------------------------------

    private fun drawVector(canvas: Canvas, vector: ImageVector, sizePx: Int, tint: Int) {
        canvas.save()
        canvas.scale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
        drawNode(canvas, vector.root, tint)
        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: VectorNode, tint: Int) {
        when (node) {
            is VectorGroup -> {
                canvas.save()
                canvas.translate(node.translationX, node.translationY)
                canvas.translate(node.pivotX, node.pivotY)
                canvas.rotate(node.rotation)
                canvas.scale(node.scaleX, node.scaleY)
                canvas.translate(-node.pivotX, -node.pivotY)
                if (node.clipPathData.isNotEmpty()) canvas.clipPath(node.clipPathData.toPath().asAndroidPath())
                node.forEach { child -> drawNode(canvas, child, tint) }
                canvas.restore()
            }
            is VectorPath -> {
                val path = node.pathData.toPath().asAndroidPath()
                path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                if (node.fill != null) canvas.drawPath(path, paint(withAlpha(tint, node.fillAlpha)))
                if (node.stroke != null && node.strokeLineWidth > 0f) {
                    canvas.drawPath(path, paint(withAlpha(tint, node.strokeAlpha), Paint.Style.STROKE, node.strokeLineWidth))
                }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (min(max(alpha, 0f), 1f) * (color ushr 24)).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
