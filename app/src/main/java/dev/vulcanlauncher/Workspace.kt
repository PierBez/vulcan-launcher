package dev.vulcanlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Horizontally paged screens (home pages, dock pages, drawer pages).
 * Reports gestures by name ("swipeUp", "doubleTap", "pinchIn", …; see [GESTURE_KEYS]) through [onGesture].
 */
class Workspace(context: Context) : ViewGroup(context) {
    var onGesture: (String) -> Unit = {}
    /** Scroll position in pages (0 = first page), for the page indicator and wallpaper. */
    var onScroll: (Float) -> Unit = {}
    /**
     * Optional live swipe up: called with px pulled up so far while the finger moves, then once more with [end] and the
     * upward velocity (px/s). Returning true takes the gesture over, so "swipeUp" isn't reported.
     */
    var onPull: ((up: Float, vy: Float, end: Boolean) -> Boolean)? = null
    private var pulling = false
    var effect = "simple"
    var infinite = false

    var current = 0
        private set
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var startScroll = 0
    private var mode = NONE
    private var velocity: VelocityTracker? = null
    private var lastTapUp = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var doubleTapDown = false
    private var handledDown = -1L
    private val start = FloatArray(4)
    private val end = FloatArray(4)
    private var anim: ValueAnimator? = null

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        for (i in 0 until childCount) getChildAt(i).measure(widthSpec, heightSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).layout(i * width, 0, (i + 1) * width, height)
        scrollTo(current * width, 0)
    }

    fun snapTo(page: Int, animate: Boolean = true) {
        val target = when {
            infinite && childCount > 1 && page >= childCount -> 0
            infinite && childCount > 1 && page < 0 -> childCount - 1
            else -> page.coerceIn(0, maxOf(0, childCount - 1))
        }
        current = target
        anim?.cancel()
        if (!animate || width == 0) { scrollTo(current * width, 0); return }
        anim = ValueAnimator.ofInt(scrollX, current * width).apply {
            duration = 250
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            start()
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (width == 0) return
        for (i in 0 until childCount) getChildAt(i).applyTransform(pageTransform(effect, (i * width - l) / width.toFloat()))
        onScroll(l / width.toFloat())
    }

    /** True when the point (in this view's coordinates) is on an icon or widget of the current page. */
    private fun onItem(x: Float, y: Float): Boolean {
        val page = getChildAt(current) as? ViewGroup ?: return false
        for (i in 0 until page.childCount) {
            val c = page.getChildAt(i)
            if (c.visibility == View.VISIBLE && x >= c.left && x < c.right && y >= c.top && y < c.bottom) return true
        }
        return false
    }

    /** Shared by intercept and touch handling; returns true once the gesture belongs to the pager. */
    private fun track(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            if (handledDown == ev.downTime) return mode != NONE
            handledDown = ev.downTime
        }
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                downX = ev.x; downY = ev.y; downTime = ev.eventTime; startScroll = scrollX; mode = NONE
                velocity?.recycle(); velocity = VelocityTracker.obtain().apply { addMovement(ev) }
                doubleTapDown = ev.eventTime - lastTapUp < doubleTapTimeout && hypot(ev.x - lastTapX, ev.y - lastTapY) < slop * 6 && !onItem(ev.x, ev.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 2 && mode != HORIZONTAL) {
                mode = MULTI
                for (i in 0..1) { start[i * 2] = ev.getX(i); start[i * 2 + 1] = ev.getY(i) }
                start.copyInto(end)
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                NONE -> {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > slop && dx > dy) mode = HORIZONTAL else if (dy > slop && dy > dx) mode = VERTICAL
                }
                MULTI -> if (ev.pointerCount >= 2) for (i in 0..1) { end[i * 2] = ev.getX(i); end[i * 2 + 1] = ev.getY(i) }
                VERTICAL -> if (!doubleTapDown) pulling = onPull?.invoke(downY - ev.y - slop, 0f, false) == true || pulling
            }
            MotionEvent.ACTION_POINTER_UP -> if (mode == MULTI) {
                twoFingerGesture(start[0], start[1], start[2], start[3], end[0], end[1], end[2], end[3], slop * 4f)?.let(onGesture)
                mode = DONE
            }
            MotionEvent.ACTION_UP -> {
                if (mode == NONE && !onItem(ev.x, ev.y) && ev.eventTime - downTime < ViewConfiguration.getLongPressTimeout()) {
                    if (doubleTapDown) { onGesture("doubleTap"); lastTapUp = 0 }
                    else { lastTapUp = ev.eventTime; lastTapX = ev.x; lastTapY = ev.y }
                }
                finish(ev)
            }
            MotionEvent.ACTION_CANCEL -> finish(ev)
        }
        return mode != NONE
    }

    private fun finish(ev: MotionEvent) {
        val tracker = velocity
        tracker?.computeCurrentVelocity(1000)
        val vx = tracker?.xVelocity ?: 0f
        val vy = tracker?.yVelocity ?: 0f
        val dx = ev.x - downX
        val dy = ev.y - downY
        val last = childCount - 1
        when {
            mode == HORIZONTAL && current == 0 && dx > slop * 6 && !infinite && scrollX <= 0 -> { snapTo(0); onGesture("swipeRight") }
            mode == HORIZONTAL && current == last && dx < -slop * 6 && !infinite && scrollX >= last * width -> { snapTo(last); onGesture("swipeLeft") }
            mode == HORIZONTAL && vx < -minFling -> snapTo(current + 1)
            mode == HORIZONTAL && vx > minFling -> snapTo(current - 1)
            mode == HORIZONTAL -> snapTo((scrollX + width / 2) / maxOf(1, width))
            mode == VERTICAL && pulling -> onPull?.invoke(-dy - slop, -vy, true)
            mode == VERTICAL && dy < -slop * 3 -> onGesture(if (doubleTapDown) "doubleTapUp" else "swipeUp")
            mode == VERTICAL && dy > slop * 3 -> onGesture(if (doubleTapDown) "doubleTapDown" else "swipeDown")
        }
        if (mode != NONE) lastTapUp = 0
        velocity?.recycle(); velocity = null; mode = NONE; doubleTapDown = false; pulling = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = track(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        if (mode == HORIZONTAL && ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val max = (childCount - 1) * width
            val x = (startScroll - (ev.x - downX)).toInt()
            scrollTo(if (infinite) x.coerceIn(0, max) else x.coerceIn(-width / 8, max + width / 8), 0)
        }
        return true
    }

    private companion object { const val NONE = 0; const val HORIZONTAL = 1; const val VERTICAL = 2; const val MULTI = 3; const val DONE = 4 }
}
