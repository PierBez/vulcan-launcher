package dev.vulcanlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

/** Horizontally paged home screens. Vertical swipes are reported, not consumed by pages. */
class Workspace(context: Context) : ViewGroup(context) {
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}

    var current = 0
        private set
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var startScroll = 0
    private var mode = 0 // 0 none, 1 horizontal, 2 vertical
    private var velocity: VelocityTracker? = null

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        for (i in 0 until childCount) getChildAt(i).measure(widthSpec, heightSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).layout(i * width, 0, (i + 1) * width, height)
        scrollTo(current * width, 0)
    }

    fun snapTo(page: Int) {
        current = page.coerceIn(0, childCount - 1)
        ValueAnimator.ofInt(scrollX, current * width).apply {
            duration = 250
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
        }.start()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; startScroll = scrollX; mode = 0
                velocity = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > slop && dx > dy) mode = 1 else if (dy > slop && dy > dx) mode = 2
            }
        }
        velocity?.addMovement(ev)
        return mode != 0
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; startScroll = scrollX; mode = 0
                if (velocity == null) velocity = VelocityTracker.obtain()
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == 0) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > slop && dx > dy) mode = 1 else if (dy > slop && dy > dx) mode = 2
                }
                if (mode == 1) scrollTo((startScroll - (ev.x - downX)).toInt().coerceIn(0, (childCount - 1) * width), 0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                val dy = ev.y - downY
                when {
                    mode == 1 && vx < -minFling -> snapTo(current + 1)
                    mode == 1 && vx > minFling -> snapTo(current - 1)
                    mode == 1 -> snapTo((scrollX + width / 2) / width)
                    mode == 2 && dy < -slop * 3 -> onSwipeUp()
                    mode == 2 && dy > slop * 3 -> onSwipeDown()
                }
                velocity?.recycle(); velocity = null; mode = 0
            }
        }
        return true
    }
}
