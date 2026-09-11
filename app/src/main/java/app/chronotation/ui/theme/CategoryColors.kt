package app.chronotation.ui.theme

import kotlin.math.*

// OKLCH -> linear sRGB; reduce chroma until the color fits the sRGB gamut.
fun oklchColor(lightness: Double, chroma: Double, hue: Double): Long {
    val angle = hue * PI / 180
    var c = chroma
    var rgb = doubleArrayOf(0.0, 0.0, 0.0)
    repeat(40) {
        val a = c * cos(angle); val b = c * sin(angle)
        val l = (lightness + .3963377774 * a + .2158037573 * b).pow(3)
        val m = (lightness - .1055613458 * a - .0638541728 * b).pow(3)
        val s = (lightness - .0894841775 * a - 1.291485548 * b).pow(3)
        rgb = doubleArrayOf(4.0767416621*l - 3.3077115913*m + .2309699292*s,
            -1.2684380046*l + 2.6097574011*m - .3413193965*s, -.0041960863*l - .7034186147*m + 1.707614701*s)
        if (rgb.all { it in 0.0..1.0 }) return packSrgb(rgb)
        c *= .9
    }
    return packSrgb(rgb)
}
private fun packSrgb(rgb: DoubleArray): Long {
    fun channel(value: Double): Long {
        val x = value.coerceIn(0.0, 1.0)
        return ((if (x <= .0031308) 12.92*x else 1.055*x.pow(1/2.4)-.055) * 255).roundToLong()
    }
    return 0xFF000000 or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
}
val categoryColorRows: List<List<Long>> = run {
    val lightness = listOf(.9, .82, .74, .66, .58, .5, .42)
    val chroma = listOf(.05, .09, .13, .16, .15, .12, .08)
    listOf(lightness.map { oklchColor(it, 0.0, 0.0) }) +
        listOf(20, 40, 65, 95, 125, 150, 180, 210, 240, 265, 290, 320, 345).map { hue ->
            lightness.mapIndexed { index, l -> oklchColor(l, chroma[index], hue.toDouble()) }
        }
}
