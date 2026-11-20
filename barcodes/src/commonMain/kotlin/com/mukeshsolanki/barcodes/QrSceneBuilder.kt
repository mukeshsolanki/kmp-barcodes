package com.mukeshsolanki.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.min

/** Builds the styled QR [Scene] of [grid] for one draw [size]. */
internal class QrSceneBuilder(
    /** Module layout. */
    private val grid: QrGrid,
    /** QR style. */
    private val style: QrStyle,
    /** Draw size. */
    private val size: Size,
) {
    /** Module side in pixels. */
    private val cell: Float = min(size.width, size.height) / grid.side

    /** Pixel position of the grid's top-left corner, centring the grid. */
    private val origin: Offset = Offset((size.width - cell * grid.side) / 2f, (size.height - cell * grid.side) / 2f)

    /** Builds the scene: light modules, dark modules, finder frames and balls, clip, logo clearing and logo. */
    fun build(): Scene {
        val paintsLight = style.lightPixel != null || style.lightBrush != null
        val dark = Path()
        val light = Path()
        for (y in 0 until grid.side) {
            for (x in 0 until grid.side) {
                addModule(x, y, dark, light.takeIf { paintsLight })
            }
        }
        val lightBrush = style.lightBrush ?: BarcodeBrush.Solid(style.background)
        val layers = listOfNotNull(
            Layer(light, lightBrush.toBrush(size)).takeIf { paintsLight },
            Layer(dark, style.darkBrush.toBrush(size)),
            Layer(framePath(), (style.frameBrush ?: style.darkBrush).toBrush(size)),
            Layer(ballPath(), (style.ballBrush ?: style.darkBrush).toBrush(size)),
        )
        return Scene(size, style.background, layers, circleClip(), accurateClear(), logoPlacement())
    }

    /** Adds module ([x], [y]) to [dark] when dark, or to [light] when light and [light] is set. */
    private fun addModule(x: Int, y: Int, dark: Path, light: Path?) {
        val left = origin.x + x * cell
        val top = origin.y + y * cell
        when {
            grid.isDark(x, y) -> with(style.darkPixel) { dark.addPixel(left, top, cell, neighbors(x, y, grid::isDark)) }
            light != null && grid.isLight(x, y) -> with(style.lightPixel ?: PixelShape.Square) {
                light.addPixel(left, top, cell, neighbors(x, y, grid::isLight))
            }
        }
    }

    /** Neighbours of ([x], [y]) that satisfy [same]. */
    private fun neighbors(x: Int, y: Int, same: (Int, Int) -> Boolean): Neighbors =
        Neighbors(top = same(x, y - 1), bottom = same(x, y + 1), left = same(x - 1, y), right = same(x + 1, y))

    /** Even-odd path of the three finder rings. */
    private fun framePath(): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        grid.finders.forEach { (fx, fy) ->
            with(style.frame) { addFrame(origin.x + fx * cell, origin.y + fy * cell, FINDER_SIZE * cell, cell) }
        }
    }

    /** Path of the three finder balls. */
    private fun ballPath(): Path = Path().apply {
        grid.finders.forEach { (fx, fy) ->
            with(style.ball) {
                addBall(origin.x + (fx + BALL_OFFSET) * cell, origin.y + (fy + BALL_OFFSET) * cell, BALL_SIZE * cell)
            }
        }
    }

    /** Circle clip of a circular code, or null for a square code. */
    private fun circleClip(): Path? = grid.circleDiameter?.let { diameter ->
        Path().apply { addOval(centredSquare(diameter.toFloat())) }
    }

    /** Exact padded logo area for [LogoPadding.Accurate] clearing, or null. */
    private fun accurateClear(): Path? {
        val logo = style.logo
        if (logo == null || !logo.clearModules || logo.padding !is LogoPadding.Accurate) return null
        val area = centredSquare(paddedLogoSide(logo, grid.codeSize))
        return Path().apply {
            when (logo.shape) {
                LogoShape.Square -> addRect(area)
                LogoShape.Circle -> addOval(area)
            }
        }
    }

    /** Pixel placement of the logo, or null without a logo. */
    private fun logoPlacement(): LogoPlacement? = style.logo?.let { logo ->
        val rect = centredSquare(logo.size * grid.codeSize)
        val clip = if (logo.shape == LogoShape.Circle) Path().apply { addOval(Rect(Offset.Zero, rect.size)) } else null
        LogoPlacement(logo, rect, clip)
    }

    /** Pixel square [modules] wide centred on the grid. */
    private fun centredSquare(modules: Float): Rect =
        Rect(origin + Offset(grid.side * cell / 2f, grid.side * cell / 2f), modules * cell / 2f)
}
