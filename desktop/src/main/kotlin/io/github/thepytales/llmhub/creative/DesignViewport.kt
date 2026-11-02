package io.github.thepytales.llmhub.creative

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import kotlin.math.pow

/**
 * Where the page sits on the canvas of the design editor (AP-24): [zoom] 1 = page fits the view, [pan] = shift from the
 * centred position in pixels. Zooming keeps the point under the mouse (or the middle of the view) in place, and the
 * page never leaves the view completely – before, zooming out and in could throw it out of sight.
 */
object DesignViewport {
    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 8f
    /** Pixels of the page that stay visible at least. */
    const val KEEP_VISIBLE = 96f

    /** Pixels per millimetre at zoom 1: the page fits the view with a margin of 32 px. */
    fun fit(view: Size, page: Size): Float = min((view.width - 64f) / page.width, (view.height - 64f) / page.height).coerceAtLeast(0.05f)

    /** Top left corner of the page in the view. */
    fun origin(view: Size, page: Size, zoom: Float, pan: Offset): Offset {
        val s = fit(view, page) * zoom
        return Offset((view.width - page.width * s) / 2, (view.height - page.height * s) / 2) + pan
    }

    /**
     * Zoom and pan after zooming by [factor] around [anchor] (view pixels; null = middle of the view): the page point
     * under the anchor stays where it is.
     */
    fun zoomAt(view: Size, page: Size, zoom: Float, pan: Offset, factor: Float, anchor: Offset? = null): Pair<Float, Offset> {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (view.width <= 0f || view.height <= 0f) return next to pan
        val fit = fit(view, page)
        val a = anchor ?: Offset(view.width / 2, view.height / 2)
        val mm = (a - origin(view, page, zoom, pan)) / (fit * zoom)
        val wanted = a - mm * (fit * next)
        val centred = origin(view, page, next, Offset.Zero)
        return next to clampPan(view, page, next, wanted - centred)
    }

    /** [pan], limited so that at least [KEEP_VISIBLE] pixels of the page (or all of a smaller page) stay in view. */
    fun clampPan(view: Size, page: Size, zoom: Float, pan: Offset): Offset {
        if (view.width <= 0f || view.height <= 0f) return pan
        val s = fit(view, page) * zoom
        val w = page.width * s
        val h = page.height * s
        val centred = origin(view, page, zoom, Offset.Zero)
        val mx = minOf(KEEP_VISIBLE, w, view.width / 2)
        val my = minOf(KEEP_VISIBLE, h, view.height / 2)
        val x = (centred.x + pan.x).coerceIn(mx - w, view.width - mx)
        val y = (centred.y + pan.y).coerceIn(my - h, view.height - my)
        return Offset(x - centred.x, y - centred.y)
    }

    /**
     * Zoom factor for a wheel step with Strg: proportional to the scroll amount, so a mouse wheel notch (±1) zooms by
     * 12 % and the many small steps of a touchpad zoom smoothly instead of 12 % each.
     */
    fun wheelFactor(deltaY: Float): Float = 1.12f.pow(-deltaY.coerceIn(-3f, 3f))
}
