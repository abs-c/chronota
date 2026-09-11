package app.chronota.domain

import kotlin.math.abs
import kotlin.math.roundToInt

// Keep the existing 0–5 storage scale so previously saved ratings retain their meaning.
fun ratingScore(value: String): Int = ((value.toDoubleOrNull() ?: 0.0) * 20).roundToInt().coerceIn(0, 100)
fun ratingValue(score: Int): String = (score.coerceIn(0, 100) / 20.0).toString()
fun ratingStars(score: Int): Float = (score.coerceIn(0, 100) / 10f).roundToInt() / 2f
fun validRating(value: String): Boolean = value.toDoubleOrNull()?.let {
    it.isFinite() && it in 0.0..5.0 && abs(it * 20 - (it * 20).roundToInt()) < .000001
} == true
