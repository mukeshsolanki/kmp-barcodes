package com.mukeshsolanki.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import kotlin.math.min

/** One path filled with one brush. */
internal class Layer(
    /** Area to fill. */
    val path: Path,
    /** Fill paint. */
    val brush: Brush,
)

/** Logo placement for one draw size. */
internal class LogoPlacement(
    /** Logo to draw. */
    val logo: Logo,
    /** Logo bounds in pixels. */
    val rect: Rect,
    /** Circle clip in logo-local coordinates, or null for a square logo. */
    val clip: Path?,
)

/** Everything drawn at one [size]: background, clipped layers, the cleared logo area and the logo. */
internal class Scene(
    /** Draw size the scene was built for. */
    val size: Size,
    /** Colour behind everything. */
    val background: Color,
    /** Layers drawn in order inside [clip]. */
    val layers: List<Layer>,
    /** Outline clip of the code, or null. */
    val clip: Path? = null,
    /** Area repainted with [background] before the logo, or null. */
    val clear: Path? = null,
    /** Logo drawn last, or null. */
    val logo: LogoPlacement? = null,
)

/** Plain scene of [matrix] for [size]: dark cells merged into horizontal runs, single-row (1D) codes stretched. */
internal fun simpleScene(matrix: BarcodeMatrix, style: SimpleStyle, size: Size): Scene {
    val stretch = matrix.height == 1
    val fit = min(size.width / matrix.width, size.height / matrix.height)
    val cellWidth = if (stretch) size.width / matrix.width else fit
    val cell = Size(cellWidth, if (stretch) size.height else cellWidth)
    val origin = Offset((size.width - matrix.width * cell.width) / 2f, (size.height - matrix.height * cell.height) / 2f)
    val path = Path()
    for (y in 0 until matrix.height) {
        path.addRuns(matrix, y, origin, cell)
    }
    return Scene(size, style.background, listOf(Layer(path, style.dark.toBrush(size))))
}

/** Adds one rect per run of consecutive dark cells in row [y] of [matrix]. */
private fun Path.addRuns(matrix: BarcodeMatrix, y: Int, origin: Offset, cell: Size) {
    val top = origin.y + y * cell.height
    var x = 0
    while (x < matrix.width) {
        val start = x
        while (x < matrix.width && matrix[x, y]) x++
        if (x > start) {
            addRect(Rect(origin.x + start * cell.width, top, origin.x + x * cell.width, top + cell.height))
        } else {
            x++
        }
    }
}

/** Converts to a Compose brush for a painter of [size]. */
internal fun BarcodeBrush.toBrush(size: Size): Brush = when (this) {
    is BarcodeBrush.Solid -> SolidColor(color)
    is BarcodeBrush.Gradient -> factory(size)
    is BarcodeBrush.Image -> ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
