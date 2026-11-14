package com.rainy.clawdpet.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

/**
 * Closed-form equivalents of renderer/css/{states,keyframes}.css for the states PetState implements.
 * Canvas has no keyframe engine, so every animation is a function of `state.modeElapsedMs`: sine waves for
 * the ease-in-out loops, smoothstep for eased ramps, and hard step functions for the desktop's `steps(1)`
 * animations (walk, tail flick, hanging feet, parachute), so the sprite keeps its hard-pixel look.
 */
object PetRenderer {
    // No anti-aliasing: abutting rects stay seamless and edges stay hard like the desktop sprite.
    private val paint = Paint()

    private fun half(t: Float, periodMs: Float): Boolean = (t % periodMs) < periodMs / 2f

    /** 0 -> 1 -> 0 over one period (an ease-in-out keyframe pair 0%/50%/100%). */
    private fun sine01(t: Float, periodMs: Float): Float =
        (1f - kotlin.math.cos((t / periodMs) * 2f * Math.PI.toFloat())) / 2f

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun fill(canvas: Canvas, r: SpriteRect, alpha: Int = 255) {
        paint.color = r.color
        paint.alpha = alpha
        canvas.drawRect(r.x, r.y, r.x + r.w, r.y + r.h, paint)
    }

    private fun fillAll(canvas: Canvas, rects: List<SpriteRect>, alpha: Int = 255) {
        for (r in rects) fill(canvas, r, alpha)
    }

    /**
     * Draws the pet with its box's top-left at the canvas origin, already mirrored for [PetState.facing].
     * The caller translates the canvas to the pet's position and scale.
     */
    fun draw(canvas: Canvas, state: PetState, sprite: ModelSprite) {
        val t = state.modeElapsedMs
        val mode = state.mode

        canvas.save()
        if (state.facing < 0f) canvas.scale(-1f, 1f, PetSprite.WIDTH / 2f, 0f)
        canvas.translate(PetSprite.ORIGIN_X, PetSprite.ORIGIN_Y)

        // #bob-group: whole-body translate / rotate / scaleY around (40, 40)
        var bobScaleY = 1f
        var bobRotate = 0f
        var bobDy = 0f
        when (mode) {
            Mode.IDLE, Mode.THINK -> bobScaleY = 1f + 0.03f * sine01(t, 2400f)
            Mode.WALK -> bobDy = if (half(t, 240f)) 0f else -2f
            Mode.JUMP -> {
                val lift = jumpLift(t)
                bobDy = -14f * lift
                bobScaleY = 0.94f + 0.11f * lift
            }
            Mode.HELD -> bobRotate = 4f * (1f - 2f * sine01(t, 900f))
            Mode.PAT -> {
                bobScaleY = 1f + 0.06f * sine01(t, 600f)
                bobDy = -sine01(t, 600f)
            }
            Mode.FALL -> {}
        }
        canvas.translate(PetSprite.BOB_PIVOT_X, PetSprite.BOB_PIVOT_Y)
        canvas.translate(0f, bobDy)
        canvas.rotate(bobRotate)
        canvas.scale(1f, bobScaleY)
        canvas.translate(-PetSprite.BOB_PIVOT_X, -PetSprite.BOB_PIVOT_Y)

        if (mode == Mode.PAT) drawHearts(canvas, sprite, t)
        if (mode == Mode.FALL && state.fallChute) drawChute(canvas, sprite, t)

        var hangDrawn = false
        for (layer in sprite.layers) {
            when (layer) {
                is Layer.Fixed -> fillAll(canvas, layer.rects)
                is Layer.Eyes -> drawEyes(canvas, state, sprite)
                is Layer.Tail -> {
                    val dy = if (mode != Mode.HELD && mode != Mode.FALL && !half(state.clockMs, 1600f)) 7.5f else 0f
                    canvas.save()
                    canvas.translate(0f, dy)
                    fillAll(canvas, layer.rects)
                    canvas.restore()
                }
                is Layer.Leg -> {
                    if (mode == Mode.HELD && sprite.hangLegs.isNotEmpty()) {
                        if (!hangDrawn) { drawHangLegs(canvas, sprite, t); hangDrawn = true }
                    } else {
                        drawLeg(canvas, layer, mode, t)
                    }
                }
                is Layer.Arm -> {
                    canvas.save()
                    canvas.translate(0f, armDy(state, t))
                    canvas.rotate(armDeg(state, layer.left, t), layer.pivotX, layer.pivotY)
                    fill(canvas, layer.rect)
                    canvas.restore()
                }
            }
        }

        canvas.restore() // bob + facing + origin offset are all inside this single save()
    }

    /** hop keyframes: 0% / 100% down, 40%-60% up, eased between. */
    private fun jumpLift(t: Float): Float {
        val p = (t % 450f) / 450f
        return when {
            p < 0.4f -> smooth(p / 0.4f)
            p < 0.6f -> 1f
            else -> smooth((1f - p) / 0.4f)
        }
    }

    private fun drawLeg(canvas: Canvas, leg: Layer.Leg, mode: Mode, t: Float) {
        var dy = 0f
        var deg = 0f
        when (mode) {
            // walk-leg-a: up in the first half; walk-leg-b: up in the second half (steps(1))
            Mode.WALK -> dy = if (half(t, 240f) == leg.isA) -3f else 0f
            Mode.JUMP -> dy = -4f * jumpLift(t)
            Mode.HELD -> {
                val s = sine01(if (leg.isA) t else t + 300f, 600f)
                dy = 2f + s
                deg = 2f - 4f * s
            }
            Mode.FALL -> dy = -3f
            else -> {}
        }
        canvas.save()
        canvas.translate(0f, dy)
        if (deg != 0f) canvas.rotate(deg, leg.pivotX, leg.pivotY)
        fillAll(canvas, leg.rects)
        canvas.restore()
    }

    private fun drawHangLegs(canvas: Canvas, sprite: ModelSprite, t: Float) {
        // hang-foot-a / -b: feet swing 3 units either way in whole steps, 0.8s per loop
        val first = half(t, 800f)
        for (leg in sprite.hangLegs) {
            fill(canvas, leg.upper)
            val dx = if (first == leg.isA) 3f else -3f
            canvas.save()
            canvas.translate(dx, 0f)
            fill(canvas, leg.foot)
            canvas.restore()
        }
    }

    private fun drawEyes(canvas: Canvas, state: PetState, sprite: ModelSprite) {
        val mode = state.mode
        val set = when {
            mode == Mode.PAT -> sprite.eyesHappy
            mode == Mode.FALL && state.fallScared -> sprite.eyesScared
            else -> sprite.eyesNormal
        }
        if (set !== sprite.eyesNormal) { fillAll(canvas, set); return }
        val sy = (if (mode == Mode.HELD || mode == Mode.FALL) 1.25f else 1f) * state.blinkScale
        for (r in set) {
            if (sy == 1f) { fill(canvas, r); continue }
            canvas.save()
            canvas.scale(1f, sy, r.x + r.w / 2f, r.y + r.h / 2f)
            fill(canvas, r)
            canvas.restore()
        }
    }

    private fun armDeg(state: PetState, left: Boolean, t: Float): Float {
        val sign = if (left) -1f else 1f
        return when (state.mode) {
            Mode.WALK -> if (half(t, 240f)) 0f else 16f * sign
            Mode.JUMP -> 16f * sign * sine01(t, 450f)
            Mode.PAT -> 16f * sign * sine01(t, 600f)
            Mode.HELD -> 6f * sign * sine01(t, 900f)
            Mode.IDLE -> 6f * sign * sine01(t, 2400f)
            Mode.THINK -> 6f * sign * sine01(t, 1800f)
            Mode.FALL -> 0f
        }
    }

    /** `flail`: arms bob 2 units either way every 0.15s while falling without a parachute. */
    private fun armDy(state: PetState, t: Float): Float {
        if (state.mode != Mode.FALL || state.fallChute) return 0f
        val p = (t % 300f) / 150f
        val tri = if (p < 1f) p else 2f - p
        return -2f + 4f * tri
    }

    private fun drawHearts(canvas: Canvas, sprite: ModelSprite, t: Float) {
        canvas.save()
        canvas.translate(sprite.heartsDx, sprite.heartsDy)
        drawHeart(canvas, PetSprites.heart1, t)
        if (t >= 600f) drawHeart(canvas, PetSprites.heart2, t - 600f)
        canvas.restore()
    }

    /** heart-float: rises 12 units over 1.2s (ease-out), fades in over the first 30%, then out. */
    private fun drawHeart(canvas: Canvas, blocks: List<SpriteRect>, t: Float) {
        val p = (t % 1200f) / 1200f
        val rise = 1f - (1f - p) * (1f - p)
        val alpha = if (p < 0.3f) p / 0.3f else 1f - (p - 0.3f) / 0.7f
        if (alpha <= 0f) return
        canvas.save()
        canvas.translate(0f, 4f - 12f * rise)
        fillAll(canvas, blocks, (alpha.coerceIn(0f, 1f) * 255).toInt())
        canvas.restore()
    }

    /**
     * chute-open (0.5s, stepped, after a 0.2s delay) then chute-bob (1.2s, steps(1), after 0.7s).
     * Scales around (40, 0) in the wrapper's local space, where the canopy meets the pet's back.
     */
    private fun drawChute(canvas: Canvas, sprite: ModelSprite, t: Float) {
        val open = t - 200f
        val (sx, sy) = when {
            open < 100f -> return
            open < 200f -> 0.25f to 0.2f
            open < 300f -> 0.55f to 0.5f
            open < 400f -> 0.85f to 0.8f
            open < 500f -> 1.08f to 1.1f
            else -> 1f to 1f
        }
        val bob = t - 700f
        val dx = if (bob < 0f) 0f else if (half(bob, 1200f)) -2f else 2f
        canvas.save()
        canvas.translate(0f, sprite.chuteDy)
        canvas.scale(1f, sprite.chuteScaleY)
        canvas.translate(40f, 0f)
        canvas.scale(sx, sy)
        canvas.translate(-40f, 0f)
        canvas.translate(dx, 0f)
        fillAll(canvas, PetSprites.chute)
        canvas.restore()
    }

    // ── speech bubble (the care message) ───────────────────────────────────────────────────────────
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2B2B") }
    private val bubbleBgPaint = Paint().apply { color = Color.parseColor("#F2F2F2") }
    private val bubbleBorderPaint = Paint().apply {
        color = Color.parseColor("#2B2B2B")
        style = Paint.Style.STROKE
    }

    /**
     * Draws the message bubble in SCREEN pixels (not inside the pet's scaled space): the pet is drawn small,
     * and text scaled down with it would be unreadable, so this uses dp-based sizes.
     */
    fun drawThinkBubble(canvas: Canvas, message: String, anchorX: Float, anchorY: Float, density: Float) {
        val textSize = 13f * density
        val paddingX = 10f * density
        val paddingY = 8f * density
        val lineHeight = textSize * 1.3f
        val maxWidth = 220f * density

        bubbleTextPaint.textSize = textSize
        bubbleBorderPaint.strokeWidth = 1.5f * density

        val lines = wrapToWidth(message, maxWidth)
        val textWidth = lines.maxOf { bubbleTextPaint.measureText(it) }
        val boxW = textWidth + paddingX * 2f
        val boxH = lines.size * lineHeight + paddingY * 2f
        val boxLeft = (anchorX - boxW / 2f).coerceAtLeast(4f * density)
        val boxTop = (anchorY - boxH - 16f * density).coerceAtLeast(4f * density)

        canvas.drawRect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH, bubbleBgPaint)
        canvas.drawRect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH, bubbleBorderPaint)

        val fm = bubbleTextPaint.fontMetrics
        lines.forEachIndexed { i, line ->
            val baseline = boxTop + paddingY + lineHeight * i - fm.ascent
            canvas.drawText(line, boxLeft + paddingX, baseline, bubbleTextPaint)
        }
    }

    private fun wrapToWidth(text: String, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (current.isEmpty() || bubbleTextPaint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
