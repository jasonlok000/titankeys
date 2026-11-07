package com.atlas.keyboard

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import com.atlas.engine.Calibration
import com.atlas.engine.TouchAlignment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a calibration profile: three touches on each of the 37 keys, then one fresh touch on each to
 * check the result. Reads the touchpad at the activity's window, like [TouchpadPreview]. Nothing changes
 * until the profile is saved.
 */
class CalibrationSession(private val activity: Activity, private val changed: (JSONObject) -> Unit) {
    private enum class Stage(val id: String) { Touch("touch"), Check("check"), Passed("passed"), Failed("failed") }

    private val touchpad = Touchpad(activity.display!!)
    private val centres = Array(KEYS) { FloatArray(2) }
    private val widths = FloatArray(KEYS)
    private val samples = Array(KEYS) { Array(TOUCHES) { FloatArray(2) } }
    private val contact = ArrayList<Sample>()
    private var stage = Stage.Touch
    private var proposed: TouchAlignment? = null
    private var collected = 0
    private var checked = 0
    private var hits = 0
    private var retry = false
    private var touching = false
    private var pressed = false
    private var readyAt = 0L
    private var touch: Sample? = null
    private var original: Window.Callback? = null
    private var installed: Window.Callback? = null
    private var activeToken: Any? = null

    init {
        var key = 0
        for ((row, labels) in Layout.rows.withIndex()) {
            var left = 0f
            for (column in labels.indices) {
                val width = if (row == 3) Layout.bottomWidths[column] else 1f
                centres[key][0] = (left + width / 2) / 10
                centres[key][1] = (row + .5f) / 4
                widths[key] = width / 10
                left += width
                key++
            }
        }
    }

    fun start() {
        if (original != null) return
        val callback = activity.window.callback
        original = callback
        val token = Any()
        activeToken = token
        val keys = { event: KeyEvent -> activeToken === token && key(event) }
        KeyCapture.waiting = keys
        installed = TouchpadWindowCallback(callback, keys) { event ->
            (activeToken === token && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        activity.window.callback = installed
    }

    fun stop() {
        original?.let { if (activity.window.callback === installed) activity.window.callback = it }
        original = null
        installed = null
        activeToken = null
        KeyCapture.waiting = null
        touching = false
        pressed = false
        contact.clear()
    }

    fun repeatCheck() {
        check(proposed != null) { "There are no touches to check" }
        beginCheck()
        publish()
    }

    fun restart() {
        proposed = null
        stage = Stage.Touch
        collected = 0
        retry = false
        touch = null
        publish()
    }

    /** Saves the checked profile under [name] and starts using it. */
    fun save(name: String) {
        check(stage == Stage.Passed) { "Every key must pass before saving" }
        Calibration.add(name, proposed!!)
    }

    fun snapshot(): JSONObject {
        val alignment = proposed
        val sample = touch
        return JSONObject()
            .put("stage", stage.id)
            .put("key", target())
            .put("keyNumber", if (stage == Stage.Touch) collected / TOUCHES + 1 else checked + 1)
            .put("touch", collected % TOUCHES + 1)
            .put("retry", retry)
            .put("canRepeat", alignment != null)
            .put("raw", if (sample == null) JSONObject.NULL else JSONArray().put(sample.x.toDouble()).put(sample.y.toDouble()))
            .put("adjusted", if (sample == null || alignment == null) JSONObject.NULL else JSONArray()
                .put(alignment.x(sample.x, sample.y).toDouble()).put(alignment.y(sample.y).toDouble()))
    }

    private fun publish() = changed(snapshot())

    private fun target() = when (stage) {
        Stage.Touch -> minOf(KEYS - 1, collected / TOUCHES)
        // Visits every key once, in an order the hand can't anticipate.
        else -> minOf(KEYS - 1, checked) * CHECK_STRIDE % KEYS
    }

    private fun beginCheck() {
        stage = Stage.Check
        checked = 0
        hits = 0
        retry = false
        touch = null
    }

    /** A pressed key means the touch was too firm, so it doesn't count. */
    private fun key(event: KeyEvent): Boolean {
        if (touchpad.isGestureKey(event) || event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (stage != Stage.Touch && stage != Stage.Check) return false
        pressed = event.action == KeyEvent.ACTION_DOWN
        if (pressed) {
            touching = false
            retry = true
            publish()
        }
        return true
    }

    private fun motion(event: MotionEvent) {
        if (stage != Stage.Touch && stage != Stage.Check) return
        // Readings stay unadjusted here: they are what the new profile is fitted to.
        val readings = touchpad.samples(event, TouchAlignment.IDENTITY)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                contact.clear()
                touching = event.eventTime >= readyAt && !pressed
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                touching = false
                return
            }
        }
        if (!touching) return
        for (reading in readings) if (reading.valid && contact.size < MAX_CONTACT) contact += reading
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        touching = false
        readyAt = event.eventTime + SETTLE_MILLIS
        val duration = if (contact.isEmpty()) 0 else contact.last().time - contact.first().time
        val xs = contact.map { it.x }.sorted()
        val ys = contact.map { it.y }.sorted()
        if (duration < MIN_HOLD_MILLIS || duration > MAX_HOLD_MILLIS || xs.size < 2
            || xs.last() - xs.first() > MAX_DRIFT_X || ys.last() - ys.first() > MAX_DRIFT_Y) {
            retry = true
            publish()
            return
        }
        retry = false
        accept(xs[xs.size / 2], ys[ys.size / 2], event.eventTime)
        publish()
    }

    private fun accept(x: Float, y: Float, time: Long) {
        if (stage == Stage.Touch) {
            val key = target()
            samples[key][collected % TOUCHES][0] = x
            samples[key][collected % TOUCHES][1] = y
            collected++
            if (collected == KEYS * TOUCHES) {
                try {
                    proposed = TouchAlignment.fit(samples, centres)
                    beginCheck()
                } catch (_: IllegalArgumentException) {
                    // The touches can't make a profile, so there is nothing to check.
                    proposed = null
                    stage = Stage.Failed
                }
            }
            return
        }
        val alignment = proposed!!
        touch = Sample(x, y, time)
        if (inside(target(), alignment.x(x, y), alignment.y(y))) hits++
        checked++
        if (checked == KEYS) {
            stage = if (hits == KEYS) Stage.Passed else Stage.Failed
            touch = null
        }
    }

    private fun inside(key: Int, x: Float, y: Float): Boolean {
        val (centreX, centreY) = centres[key]
        val halfWidth = widths[key] / 2
        val right = centreX + halfWidth
        val bottom = centreY + .125f
        return x >= centreX - halfWidth && (x < right || right >= .99999f && x <= 1)
            && y >= centreY - .125f && (y < bottom || bottom == 1f && y <= 1)
    }

    private companion object {
        const val KEYS = 37
        const val TOUCHES = 3
        const val CHECK_STRIDE = 11
        const val MAX_CONTACT = 512
        const val SETTLE_MILLIS = 250L
        const val MIN_HOLD_MILLIS = 80L
        const val MAX_HOLD_MILLIS = 2500L
        const val MAX_DRIFT_X = .07f
        const val MAX_DRIFT_Y = .14f
    }
}
