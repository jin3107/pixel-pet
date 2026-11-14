package com.rainy.clawdpet.wallpaper

/**
 * Data model for the sprites in PetSprites.kt (auto-generated from the desktop app's
 * renderer/index.html by scripts/generate-sprites.js). Coordinates are the desktop
 * template's own "local sprite units"; PetRenderer maps them into the pet's box.
 */
data class SpriteRect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int)

/** A rectangle in local sprite units (used for the touch "head" zone). */
data class Zone(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

/** Clawd's legs while held by the scruff: [upper] stays put, [foot] swings sideways. */
data class HangLeg(val upper: SpriteRect, val foot: SpriteRect, val isA: Boolean)

/** One piece of a sprite, in draw order. */
sealed class Layer {
    /** Rects that never move on their own (body, head, ears, wool...). */
    class Fixed(val rects: List<SpriteRect>) : Layer()

    /** Where the current eye set (normal / happy / scared) is drawn. */
    object Eyes : Layer()

    /** A leg (one or more rects) that swings about its hip. [isA] picks the walk phase. */
    class Leg(val rects: List<SpriteRect>, val pivotX: Float, val pivotY: Float, val isA: Boolean) : Layer()

    /** The cat's dark tail tip, which flicks one cell down and up. */
    class Tail(val rects: List<SpriteRect>) : Layer()

    /** Clawd's arm, rotated about its shoulder. */
    class Arm(val rect: SpriteRect, val pivotX: Float, val pivotY: Float, val left: Boolean) : Layer()
}

class ModelSprite(
    val id: String,
    val layers: List<Layer>,
    val eyesNormal: List<SpriteRect>,
    val eyesHappy: List<SpriteRect>,
    val eyesScared: List<SpriteRect>,
    val hangLegs: List<HangLeg>,
    val headZone: Zone,
    /** Translate applied to the hearts (they sit above the head, which differs per model). */
    val heartsDx: Float,
    val heartsDy: Float,
    /** Where the parachute attaches to the pet's back, and its vertical stretch. */
    val chuteDy: Float,
    val chuteScaleY: Float,
)

/**
 * The pet's box. The box is what PetState moves around and what touch hit-testing uses; sprite local
 * coordinates map into it with `box = local + ORIGIN`. Local x = 40 is the mirror axis, so the box is
 * symmetric about its middle (x = 50) and a facing flip never moves the pet.
 */
object PetSprite {
    const val WIDTH = 100f
    const val HEIGHT = 68f
    const val ORIGIN_X = 10f
    const val ORIGIN_Y = 10f
    /** Local y of the feet; the box extends HEIGHT - (FEET_Y + ORIGIN_Y) below them. */
    const val FEET_Y = 55f

    /** #bob-group transform-origin from base.css. */
    const val BOB_PIVOT_X = 40f
    const val BOB_PIVOT_Y = 40f
}
