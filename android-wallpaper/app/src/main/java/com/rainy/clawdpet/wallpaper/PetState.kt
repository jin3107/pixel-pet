package com.rainy.clawdpet.wallpaper

import kotlin.math.abs
import kotlin.random.Random

/**
 * Trimmed port of the desktop main.js tick() state machine: the states needed to live and roam the home
 * screen. IDLE, WALK, JUMP, HELD (finger drag), FALL (release, sometimes with a parachute and/or scared
 * eyes), PAT (press-hold the head), THINK (care message). The desktop-only tricks (code/music/soccer/
 * surf/climb/coffee/butterfly) are not ported.
 */
enum class Mode { IDLE, WALK, JUMP, HELD, FALL, PAT, THINK }

class PetState {
    var mode: Mode = Mode.IDLE
        private set

    var currentX = 0f
    var currentY = 0f
    var facing = 1f
        private set

    var modeElapsedMs = 0f
        private set

    /** Monotonic clock for animations that run regardless of mode (the cat's tail flick). */
    var clockMs = 0f
        private set

    var currentMessage: String? = null
        private set

    /** Chosen per fall, like the desktop's fallChute / fallScared. */
    var fallChute = false
        private set
    var fallScared = false
        private set

    /** 0.25 for the middle of a blink, 1 otherwise (blink = 250ms, steps(1): closed from 30% to 70%). */
    val blinkScale: Float
        get() = if (blinkElapsedMs in 75f..175f) 0.25f else 1f

    private var blinkElapsedMs = -1f
    private var nextBlinkInMs = nextBlinkDelay()

    private var targetX = 0f
    private var framesLeft = 60f
    private var vy = 0f
    private var dragging = false
    private var patting = false
    private var tickN = 0
    private var boxW = 0f
    private var boxH = 0f
    private var messageTimerMs = 0f

    fun onSurfaceSize(w: Float, h: Float) {
        val firstLayout = boxW == 0f && boxH == 0f
        boxW = w
        boxH = h
        if (firstLayout) {
            currentX = clampX(boxW / 2f - PET_W / 2f)
            currentY = baseY()
        } else {
            currentX = clampX(currentX)
            if (mode != Mode.HELD && mode != Mode.FALL) currentY = baseY()
        }
    }

    fun baseY() = boxH - PET_H + GROUND_PAD
    fun clampX(x: Float) = x.coerceIn(0f, (boxW - PET_W).coerceAtLeast(0f))

    fun startDrag(x: Float, y: Float) {
        dragging = true
        patting = false
        currentX = clampX(x)
        currentY = y
        setMode(Mode.HELD)
    }

    fun moveDrag(x: Float, y: Float) {
        if (!dragging) return
        currentX = clampX(x)
        currentY = y.coerceIn(-PET_H, boxH - 4f)
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        vy = 0f
        fallChute = Random.nextFloat() < 0.35f
        fallScared = Random.nextFloat() < 0.5f
        setMode(Mode.FALL)
    }

    fun setPatting(on: Boolean) {
        if (!dragging) patting = on
    }

    /** Care-message bubble (mirrors desktop main.js's showCareMessage()). */
    fun showMessage(message: String, durationMs: Float) {
        if (dragging || mode == Mode.FALL || mode == Mode.HELD) return
        currentMessage = message
        messageTimerMs = durationMs
        setMode(Mode.THINK)
    }

    private fun setMode(next: Mode) {
        if (mode != next) modeElapsedMs = 0f
        mode = next
    }

    private fun startIdle() {
        setMode(Mode.IDLE)
        framesLeft = 60f + Random.nextFloat() * 180f
    }

    private fun pickTarget(minDist: Float): Float {
        val maxX = (boxW - PET_W).coerceAtLeast(0f)
        var t: Float
        var tries = 0
        do {
            t = Random.nextFloat() * maxX
            tries++
        } while (abs(t - currentX) < minDist && tries < 20)
        return t
    }

    private fun nextAction() {
        if (Random.nextFloat() < 0.6f) {
            setMode(Mode.WALK)
            targetX = pickTarget(60f)
        } else {
            setMode(Mode.JUMP)
            framesLeft = 45f
        }
    }

    private fun nextBlinkDelay() = 3500f + Random.nextFloat() * 4000f

    private fun tickBlink(deltaMs: Float) {
        if (blinkElapsedMs >= 0f) {
            blinkElapsedMs += deltaMs
            if (blinkElapsedMs >= 250f) {
                blinkElapsedMs = -1f
                nextBlinkInMs = nextBlinkDelay()
            }
        } else {
            nextBlinkInMs -= deltaMs
            if (nextBlinkInMs <= 0f) blinkElapsedMs = 0f
        }
    }

    /** Advance the simulation by [deltaMs]. Call once per frame (~30ms). */
    fun tick(deltaMs: Float) {
        tickN++
        modeElapsedMs += deltaMs
        clockMs += deltaMs
        tickBlink(deltaMs)
        val by = baseY()

        when {
            dragging -> { /* position already driven by moveDrag() */ }

            mode == Mode.FALL -> {
                // with a parachute the fall is slow and capped, like desktop main.js
                if (fallChute) vy = (vy + 0.15f).coerceAtMost(1.6f) else vy += 0.9f
                currentY += vy
                currentX = clampX(currentX)
                if (currentY >= by) {
                    currentY = by
                    if (vy > 5f) vy = -vy * 0.35f else startIdle()
                }
            }

            patting && (mode == Mode.IDLE || mode == Mode.WALK || mode == Mode.JUMP || mode == Mode.PAT) -> {
                setMode(Mode.PAT)
                currentY = by
            }

            mode == Mode.PAT -> startIdle()

            mode == Mode.THINK -> {
                currentY = by
                messageTimerMs -= deltaMs
                if (messageTimerMs <= 0f) {
                    currentMessage = null
                    startIdle()
                }
            }

            mode == Mode.WALK -> {
                val dx = targetX - currentX
                if (abs(dx) < 4f) {
                    startIdle()
                } else if (tickN % 2 == 0) {
                    facing = if (dx > 0) 1f else -1f
                    currentX += facing * WALK_SPEED
                }
                currentY = by
            }

            else -> {
                currentY = by
                framesLeft -= deltaMs / FAST_MS
                if (framesLeft <= 0f) {
                    if (mode == Mode.IDLE) nextAction() else startIdle()
                }
            }
        }

        currentX = clampX(currentX)
    }

    companion object {
        const val PET_W = PetSprite.WIDTH
        const val PET_H = PetSprite.HEIGHT
        /** Box bottom sits this far below the feet-on-ground line (box y of the feet = HEIGHT - GROUND_PAD). */
        const val GROUND_PAD = PetSprite.HEIGHT - (PetSprite.FEET_Y + PetSprite.ORIGIN_Y)
        const val WALK_SPEED = 3.2f
        const val FAST_MS = 30f
    }
}
