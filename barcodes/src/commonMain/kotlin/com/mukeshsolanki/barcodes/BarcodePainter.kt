package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter

/**
 * Remembers a vector [Painter] for [content] encoded as [format], re-encoding only when an argument changes.
 * @throws IllegalArgumentException when the content cannot be encoded.
 */
@Composable
public fun rememberBarcodePainter(
    content: String,
    format: BarcodeFormat = BarcodeFormat.QrCode,
    options: EncodeOptions? = null,
    style: BarcodeStyle = QrStyle(),
): Painter = remember(content, format, options, style) { BarcodePainter(content, format, options, style) }

/**
 * Creates a vector [Painter] for [content] encoded as [format]. A [QrStyle] on a non-QR format falls back to
 * a [SimpleStyle] with its dark brush and background.
 * @throws IllegalArgumentException when the content cannot be encoded.
 */
@Suppress("FunctionName", "FunctionNaming")
public fun BarcodePainter(
    content: String,
    format: BarcodeFormat = BarcodeFormat.QrCode,
    options: EncodeOptions? = null,
    style: BarcodeStyle = QrStyle(),
): Painter = BarcodeVectorPainter(BarcodeEncoder.encode(content, format, 0, 0, options), format, style)

/** Vector painter of an encoded [matrix]; builds its paths lazily and caches them for the last draw size. */
internal class BarcodeVectorPainter(
    /** Encoded modules, quiet zone included. */
    private val matrix: BarcodeMatrix,
    /** Encoded format. */
    format: BarcodeFormat,
    /** Requested style. */
    style: BarcodeStyle,
) : Painter() {
    /** QR style when the full QR renderer applies, else null. */
    private val qrStyle: QrStyle? = (style as? QrStyle)?.takeIf { format == BarcodeFormat.QrCode }

    /** Style used by the plain renderer. */
    private val simpleStyle: SimpleStyle = when (style) {
        is SimpleStyle -> style
        is QrStyle -> SimpleStyle(dark = style.darkBrush, background = style.background)
    }

    /** QR module grid with logo modules cleared, or null for the plain renderer. */
    private val grid: QrGrid? = qrStyle?.let { buildQrGrid(matrix, it) }

    /** Alpha applied by the caller. */
    private var alpha: Float = 1f

    /** Colour filter applied by the caller. */
    private var colorFilter: ColorFilter? = null

    /** Scene cached for the last draw size. */
    private var scene: Scene? = null

    /** Unspecified: the painter is vector and fills any size. */
    override val intrinsicSize: Size get() = Size.Unspecified

    /** Stores [alpha] for every draw call. */
    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    /** Stores [colorFilter] for every draw call. */
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    /** Draws the cached scene, rebuilding it when the size changed. */
    override fun DrawScope.onDraw() {
        val current = scene?.takeIf { it.size == size } ?: buildScene(size).also { scene = it }
        drawScene(current)
    }

    /** Builds the scene for [size] with the QR or the plain renderer. */
    private fun buildScene(size: Size): Scene {
        val style = qrStyle
        val grid = grid
        return if (style != null && grid != null) {
            QrSceneBuilder(grid, style, size).build()
        } else {
            simpleScene(matrix, simpleStyle, size)
        }
    }

    /** Draws [scene] with the caller's alpha and colour filter. */
    private fun DrawScope.drawScene(scene: Scene) {
        drawRect(scene.background, alpha = alpha, colorFilter = colorFilter)
        val clip = scene.clip
        if (clip == null) drawLayers(scene.layers) else clipPath(clip) { drawLayers(scene.layers) }
        scene.clear?.let { drawPath(it, scene.background, alpha = alpha, colorFilter = colorFilter) }
        scene.logo?.let { drawLogo(it) }
    }

    /** Fills every layer in order. */
    private fun DrawScope.drawLayers(layers: List<Layer>) {
        layers.forEach { drawPath(it.path, it.brush, alpha = alpha, colorFilter = colorFilter) }
    }

    /** Draws the logo painter into its rect, clipped to a circle when requested. */
    private fun DrawScope.drawLogo(placement: LogoPlacement) {
        translate(placement.rect.left, placement.rect.top) {
            val clip = placement.clip
            if (clip == null) drawLogoPainter(placement) else clipPath(clip) { drawLogoPainter(placement) }
        }
    }

    /** Draws the logo painter at the placement size. */
    private fun DrawScope.drawLogoPainter(placement: LogoPlacement) {
        with(placement.logo.painter) { draw(placement.rect.size, alpha, colorFilter) }
    }
}
