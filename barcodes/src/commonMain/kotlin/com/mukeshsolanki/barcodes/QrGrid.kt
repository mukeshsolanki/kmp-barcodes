package com.mukeshsolanki.barcodes

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/** Side of a QR finder pattern in modules. */
internal const val FINDER_SIZE: Int = 7

/** Offset of the finder ball inside the finder pattern, in modules. */
internal const val BALL_OFFSET: Int = 2

/** Side of a QR finder ball in modules. */
internal const val BALL_SIZE: Int = 3

/** Fixed seed of the decorative ring so a circular code always renders the same. */
private const val CIRCLE_SEED = 0x5EED

/** Light modules kept between the symbol and the decorative ring. */
private const val CIRCLE_GAP = 1

/** Extra ring modules beyond the minimum that fits the symbol's corners inside the circle. */
private const val CIRCLE_RING_EXTRA = 2

/** Offset from a module's corner to its centre, in modules. */
private const val HALF_MODULE = 0.5f

/**
 * A QR symbol laid out on a square grid of [side] modules, the symbol's top-left module at
 * ([codeOrigin], [codeOrigin]) and [codeSize] modules wide.
 */
internal class QrGrid(
    /** Grid side in modules, quiet zone included. */
    val side: Int,
    /** Grid coordinate of the symbol's first module on both axes. */
    val codeOrigin: Int,
    /** Symbol side in modules, quiet zone excluded. */
    val codeSize: Int,
    /** Row-major dark flags. */
    private val dark: BooleanArray,
    /** Row-major flags of modules inside the code outline. */
    private val paintable: BooleanArray,
    /** Diameter in modules of the circular outline, or null for a square code. */
    val circleDiameter: Int?,
) {
    /** Top-left grid coordinates of the three finder patterns. */
    val finders: List<Pair<Int, Int>> = listOf(
        codeOrigin to codeOrigin,
        codeOrigin + codeSize - FINDER_SIZE to codeOrigin,
        codeOrigin to codeOrigin + codeSize - FINDER_SIZE,
    )

    /** Row-major flags of modules never drawn as data (finders and cleared logo area). */
    private val skip: BooleanArray = BooleanArray(side * side) { isFinderCell(it % side, it / side) }

    /** True when ([x], [y]) is a dark data module that is drawn. */
    fun isDark(x: Int, y: Int): Boolean = inBounds(x, y) && (y * side + x).let { dark[it] && !skip[it] }

    /** True when ([x], [y]) is a light data module inside the code outline that is drawn. */
    fun isLight(x: Int, y: Int): Boolean =
        inBounds(x, y) && (y * side + x).let { !dark[it] && paintable[it] && !skip[it] }

    /** Marks every module for which [covered] returns true as not drawn. */
    fun clear(covered: (x: Int, y: Int) -> Boolean) {
        for (index in skip.indices) {
            if (covered(index % side, index / side)) skip[index] = true
        }
    }

    /** True when ([x], [y]) lies inside the grid. */
    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until side && y in 0 until side

    /** True when ([x], [y]) lies inside one of the finder patterns. */
    private fun isFinderCell(x: Int, y: Int): Boolean =
        finders.any { (fx, fy) -> x in fx until fx + FINDER_SIZE && y in fy until fy + FINDER_SIZE }
}

/** Lays [matrix] out for [style]'s code shape and clears the modules under a naturally padded logo. */
internal fun buildQrGrid(matrix: BarcodeMatrix, style: QrStyle): QrGrid {
    val grid = when (style.codeShape) {
        CodeShape.Square -> squareQrGrid(matrix)
        CodeShape.Circle -> circleQrGrid(matrix)
    }
    val logo = style.logo
    if (logo != null && logo.clearModules && logo.padding is LogoPadding.Natural) {
        val centre = grid.side / 2f
        val half = paddedLogoSide(logo, grid.codeSize) / 2f
        grid.clear { x, y -> cellTouches(x, y, centre, half, logo.shape) }
    }
    return grid
}

/** Side in modules of the padded logo area for a symbol [codeSize] modules wide. */
internal fun paddedLogoSide(logo: Logo, codeSize: Int): Float {
    val fraction = when (val padding = logo.padding) {
        is LogoPadding.Natural -> padding.fraction
        is LogoPadding.Accurate -> padding.fraction
    }
    return logo.size * codeSize * (1f + 2f * fraction.coerceAtLeast(0f))
}

/** True when module ([x], [y]) overlaps the square or circle of half-side [half] centred at ([centre], [centre]). */
private fun cellTouches(x: Int, y: Int, centre: Float, half: Float, shape: LogoShape): Boolean = when (shape) {
    LogoShape.Square -> spanTouches(x, centre, half) && spanTouches(y, centre, half)
    LogoShape.Circle -> hypot(nearestOffset(x, centre), nearestOffset(y, centre)) < half
}

/** True when the module span [start, start + 1) overlaps the open interval of half-width [half] around [centre]. */
private fun spanTouches(start: Int, centre: Float, half: Float): Boolean =
    start < centre + half && start + 1 > centre - half

/** Offset from [centre] to the nearest point of the module span [start, start + 1]. */
private fun nearestOffset(start: Int, centre: Float): Float = centre.coerceIn(start.toFloat(), start + 1f) - centre

/** Width of the light quiet zone, found as the first dark module on the diagonal (the top-left finder corner). */
private fun BarcodeMatrix.quietZone(): Int = (0 until minOf(width, height)).firstOrNull { this[it, it] } ?: 0

/** Lays the QR [matrix] out unchanged; only the symbol area (quiet zone excluded) is paintable. */
private fun squareQrGrid(matrix: BarcodeMatrix): QrGrid {
    val side = matrix.width
    val quiet = matrix.quietZone()
    val codeSize = side - 2 * quiet
    val dark = BooleanArray(side * side) { matrix[it % side, it / side] }
    val paintable = BooleanArray(side * side) {
        (it % side - quiet) in 0 until codeSize && (it / side - quiet) in 0 until codeSize
    }
    return QrGrid(side, quiet, codeSize, dark, paintable, circleDiameter = null)
}

/**
 * Lays the QR [matrix] out inside a circle that contains the whole symbol, filling the ring between the
 * symbol and the circle with pseudo-random modules from a fixed seed, separated from the symbol by a light gap.
 */
private fun circleQrGrid(matrix: BarcodeMatrix): QrGrid {
    val quiet = matrix.quietZone()
    val codeSize = matrix.width - 2 * quiet
    val ring = ceil(codeSize * (sqrt(2f) - 1f) / 2f).toInt() + CIRCLE_RING_EXTRA
    val diameter = codeSize + 2 * ring
    val side = diameter + 2 * quiet
    val origin = quiet + ring
    val centre = side / 2f
    val paintable = BooleanArray(side * side) {
        hypot(it % side + HALF_MODULE - centre, it / side + HALF_MODULE - centre) <= diameter / 2f
    }
    val random = Random(CIRCLE_SEED)
    val dark = BooleanArray(side * side) { index ->
        circleModule(matrix, index % side - origin, index / side - origin, quiet, codeSize) {
            paintable[index] && random.nextBoolean()
        }
    }
    return QrGrid(side, origin, codeSize, dark, paintable, circleDiameter = diameter)
}

/**
 * Colour of a circular-grid module at symbol-relative ([cx], [cy]): the symbol module inside the symbol,
 * light inside the gap, else [decoration].
 */
@Suppress("LongParameterList")
private inline fun circleModule(
    matrix: BarcodeMatrix,
    cx: Int,
    cy: Int,
    quiet: Int,
    codeSize: Int,
    decoration: () -> Boolean,
): Boolean = when {
    cx in 0 until codeSize && cy in 0 until codeSize -> matrix[cx + quiet, cy + quiet]
    cx in -CIRCLE_GAP until codeSize + CIRCLE_GAP && cy in -CIRCLE_GAP until codeSize + CIRCLE_GAP -> false
    else -> decoration()
}
