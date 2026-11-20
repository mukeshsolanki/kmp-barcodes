package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/** Default logo side as a fraction of the code side. */
private const val DEFAULT_LOGO_SIZE = 0.25f

/** Default logo padding per side as a fraction of the logo size. */
private const val DEFAULT_LOGO_PADDING = 0.1f

/** Visual style of a barcode painter: [SimpleStyle] for any format, [QrStyle] for full QR styling. */
public sealed interface BarcodeStyle

/** Plain style: dark cells filled with [dark] on a [background] colour. */
@Immutable
public data class SimpleStyle(
    /** Brush for dark cells. */
    public val dark: BarcodeBrush = BarcodeBrush.Solid(Color.Black),
    /** Colour behind the whole painter. */
    public val background: Color = Color.White,
) : BarcodeStyle

/** Full QR style: module, finder and code shapes, brushes and an optional centred logo. */
@Immutable
public data class QrStyle(
    /** Shape of every dark data module. */
    public val darkPixel: PixelShape = PixelShape.Square,
    /** Shape of light data modules; light modules are painted only when this or [lightBrush] is set. */
    public val lightPixel: PixelShape? = null,
    /** Shape of the 3x3 centre of each finder pattern. */
    public val ball: BallShape = BallShape.Square,
    /** Shape of the 7x7 ring of each finder pattern. */
    public val frame: FrameShape = FrameShape.Square,
    /** Outline of the whole code. */
    public val codeShape: CodeShape = CodeShape.Square,
    /** Brush for dark data modules. */
    public val darkBrush: BarcodeBrush = BarcodeBrush.Solid(Color.Black),
    /** Brush for light data modules; null paints them with [background]. */
    public val lightBrush: BarcodeBrush? = null,
    /** Brush for finder balls; null uses [darkBrush]. */
    public val ballBrush: BarcodeBrush? = null,
    /** Brush for finder frames; null uses [darkBrush]. */
    public val frameBrush: BarcodeBrush? = null,
    /** Colour behind the whole painter. */
    public val background: Color = Color.White,
    /** Optional logo drawn in the centre. */
    public val logo: Logo? = null,
) : BarcodeStyle

/** Outline of a QR code. [Circle] clips to a circle and fills the extra ring with deterministic decorative modules. */
public enum class CodeShape {
    /** The regular square code. */
    Square,

    /** A circular code with a decorative ring around the square symbol. */
    Circle,
}

/** Paint source for barcode parts. */
@Immutable
public sealed interface BarcodeBrush {
    /** A single solid [color]. */
    @Immutable
    public data class Solid(
        /** Fill colour. */
        public val color: Color,
    ) : BarcodeBrush

    /** A brush built for the painter's draw size, e.g. a gradient. */
    @Immutable
    public class Gradient(
        /** Builds the brush for the current draw size. */
        public val factory: (Size) -> Brush,
    ) : BarcodeBrush

    /** An image used as a repeating shader. */
    @Immutable
    public class Image(
        /** Source image. */
        public val bitmap: ImageBitmap,
    ) : BarcodeBrush
}

/** A logo drawn in the centre of a QR code. [size] is the logo side as a fraction of the code side. */
@Immutable
public data class Logo(
    /** Painter that draws the logo. */
    public val painter: Painter,
    /** Logo side as a fraction (0..1) of the code side, quiet zone excluded. */
    public val size: Float = DEFAULT_LOGO_SIZE,
    /** Space cleared around the logo. */
    public val padding: LogoPadding = LogoPadding.Natural(DEFAULT_LOGO_PADDING),
    /** Outline of the logo and its cleared area. */
    public val shape: LogoShape = LogoShape.Square,
    /** Whether modules behind the logo are removed; keep the error correction high when true. */
    public val clearModules: Boolean = true,
) {
    init {
        require(size in 0f..1f) { "Logo size must be within 0..1" }
    }
}

/** How the area around a logo is cleared. */
@Immutable
public sealed interface LogoPadding {
    /** Clears every whole module touching the padded area. */
    @Immutable
    public data class Natural(
        /** Padding per side as a fraction of the logo size. */
        public val fraction: Float,
    ) : LogoPadding

    /** Clears exactly the padded area, cutting modules at its edge. */
    @Immutable
    public data class Accurate(
        /** Padding per side as a fraction of the logo size. */
        public val fraction: Float,
    ) : LogoPadding
}

/** Outline of a logo and its cleared area. */
public enum class LogoShape {
    /** Square logo area. */
    Square,

    /** Circular logo area; the logo is clipped to the circle. */
    Circle,
}
