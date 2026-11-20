package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path

/** Which of a module's four direct neighbours are painted with the same kind (dark or light). */
@Immutable
public data class Neighbors(
    /** Neighbour above. */
    public val top: Boolean,
    /** Neighbour below. */
    public val bottom: Boolean,
    /** Neighbour to the left. */
    public val left: Boolean,
    /** Neighbour to the right. */
    public val right: Boolean,
)

/** Adds one QR data module of side [size] at ([left], [top]) to the receiver path (non-zero fill). */
public fun interface PixelShape {
    /** Adds the module outline to this path. */
    public fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors)

    /** Built-in module shapes. */
    public companion object {
        /** A full square module. */
        public val Square: PixelShape = SquarePixel

        /** A circle inscribed in the module. */
        public val Circle: PixelShape = CirclePixel

        /** Vertical bars that join modules stacked on top of each other. */
        public val VerticalLines: PixelShape = VerticalLinePixel

        /** Horizontal bars that join modules side by side. */
        public val HorizontalLines: PixelShape = HorizontalLinePixel

        /** A square whose free corners (no neighbour on either side) are rounded by [radiusFraction] (0..0.5). */
        @Suppress("FunctionName", "FunctionNaming")
        public fun RoundCorners(radiusFraction: Float = DEFAULT_RADIUS): PixelShape = RoundCornerPixel(radiusFraction)
    }
}

/** Adds the 3x3 centre of a finder pattern, side [size] at ([left], [top]), to the receiver path. */
public fun interface BallShape {
    /** Adds the ball outline to this path. */
    public fun Path.addBall(left: Float, top: Float, size: Float)

    /** Built-in ball shapes. */
    public companion object {
        /** A square ball. */
        public val Square: BallShape = SquareBall

        /** A circular ball. */
        public val Circle: BallShape = CircleBall

        /** A square ball with corners rounded by [radiusFraction] of its side (0..0.5). */
        @Suppress("FunctionName", "FunctionNaming")
        public fun RoundCorners(radiusFraction: Float = DEFAULT_RADIUS): BallShape = RoundCornerBall(radiusFraction)
    }
}

/**
 * Adds the 7x7 ring of a finder pattern, side [size] at ([left], [top]) with ring width [moduleSize], to the path.
 * The path is filled with the even-odd rule, so add the outer and the inner outline.
 */
public fun interface FrameShape {
    /** Adds the frame outlines to this path. */
    public fun Path.addFrame(left: Float, top: Float, size: Float, moduleSize: Float)

    /** Built-in frame shapes. */
    public companion object {
        /** A square ring. */
        public val Square: FrameShape = SquareFrame

        /** A circular ring. */
        public val Circle: FrameShape = CircleFrame

        /** A square ring with corners rounded by [radiusFraction] of its side (0..0.5). */
        @Suppress("FunctionName", "FunctionNaming")
        public fun RoundCorners(radiusFraction: Float = DEFAULT_RADIUS): FrameShape = RoundCornerFrame(radiusFraction)
    }
}

/** Default corner radius fraction for the rounded built-in shapes. */
private const val DEFAULT_RADIUS = 0.25f

/** Largest corner radius fraction (a half side makes a full round end). */
private const val MAX_RADIUS = 0.5f

/** Thickness of a line module relative to the module side. */
private const val LINE_WIDTH = 0.8f

/** Square module. */
private data object SquarePixel : PixelShape {
    /** Adds the full module square. */
    override fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors) {
        addRect(square(left, top, size))
    }
}

/** Circular module. */
private data object CirclePixel : PixelShape {
    /** Adds the inscribed circle. */
    override fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors) {
        addOval(square(left, top, size))
    }
}

/** Neighbour-aware rounded module. */
private data class RoundCornerPixel(
    /** Corner radius as a fraction of the module side. */
    val radiusFraction: Float,
) : PixelShape {
    /** Rounds only the corners with no neighbour on either adjoining side. */
    override fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors) {
        val r = CornerRadius(size * radiusFraction.coerceIn(0f, MAX_RADIUS))
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + size,
                bottom = top + size,
                topLeftCornerRadius = r.unless(neighbors.top || neighbors.left),
                topRightCornerRadius = r.unless(neighbors.top || neighbors.right),
                bottomRightCornerRadius = r.unless(neighbors.bottom || neighbors.right),
                bottomLeftCornerRadius = r.unless(neighbors.bottom || neighbors.left),
            ),
        )
    }
}

/** Vertical line segment module. */
private data object VerticalLinePixel : PixelShape {
    /** Adds a narrow bar, capped where there is no vertical neighbour. */
    override fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors) {
        val inset = size * (1f - LINE_WIDTH) / 2f
        val cap = CornerRadius(size * LINE_WIDTH / 2f)
        addRoundRect(
            RoundRect(
                left = left + inset,
                top = top,
                right = left + size - inset,
                bottom = top + size,
                topLeftCornerRadius = cap.unless(neighbors.top),
                topRightCornerRadius = cap.unless(neighbors.top),
                bottomRightCornerRadius = cap.unless(neighbors.bottom),
                bottomLeftCornerRadius = cap.unless(neighbors.bottom),
            ),
        )
    }
}

/** Horizontal line segment module. */
private data object HorizontalLinePixel : PixelShape {
    /** Adds a narrow bar, capped where there is no horizontal neighbour. */
    override fun Path.addPixel(left: Float, top: Float, size: Float, neighbors: Neighbors) {
        val inset = size * (1f - LINE_WIDTH) / 2f
        val cap = CornerRadius(size * LINE_WIDTH / 2f)
        addRoundRect(
            RoundRect(
                left = left,
                top = top + inset,
                right = left + size,
                bottom = top + size - inset,
                topLeftCornerRadius = cap.unless(neighbors.left),
                topRightCornerRadius = cap.unless(neighbors.right),
                bottomRightCornerRadius = cap.unless(neighbors.right),
                bottomLeftCornerRadius = cap.unless(neighbors.left),
            ),
        )
    }
}

/** Square finder ball. */
private data object SquareBall : BallShape {
    /** Adds the ball square. */
    override fun Path.addBall(left: Float, top: Float, size: Float) {
        addRect(square(left, top, size))
    }
}

/** Circular finder ball. */
private data object CircleBall : BallShape {
    /** Adds the ball circle. */
    override fun Path.addBall(left: Float, top: Float, size: Float) {
        addOval(square(left, top, size))
    }
}

/** Rounded finder ball. */
private data class RoundCornerBall(
    /** Corner radius as a fraction of the ball side. */
    val radiusFraction: Float,
) : BallShape {
    /** Adds the rounded ball. */
    override fun Path.addBall(left: Float, top: Float, size: Float) {
        addRoundRect(roundSquare(left, top, size, size * radiusFraction.coerceIn(0f, MAX_RADIUS)))
    }
}

/** Square finder ring. */
private data object SquareFrame : FrameShape {
    /** Adds the outer and inner squares. */
    override fun Path.addFrame(left: Float, top: Float, size: Float, moduleSize: Float) {
        addRect(square(left, top, size))
        addRect(square(left + moduleSize, top + moduleSize, size - 2 * moduleSize))
    }
}

/** Circular finder ring. */
private data object CircleFrame : FrameShape {
    /** Adds the outer and inner circles. */
    override fun Path.addFrame(left: Float, top: Float, size: Float, moduleSize: Float) {
        addOval(square(left, top, size))
        addOval(square(left + moduleSize, top + moduleSize, size - 2 * moduleSize))
    }
}

/** Rounded finder ring. */
private data class RoundCornerFrame(
    /** Corner radius as a fraction of the frame side. */
    val radiusFraction: Float,
) : FrameShape {
    /** Adds the outer and inner rounded squares, the inner radius shrunk by the ring width. */
    override fun Path.addFrame(left: Float, top: Float, size: Float, moduleSize: Float) {
        val radius = size * radiusFraction.coerceIn(0f, MAX_RADIUS)
        addRoundRect(roundSquare(left, top, size, radius))
        val innerRadius = (radius - moduleSize).coerceAtLeast(0f)
        addRoundRect(roundSquare(left + moduleSize, top + moduleSize, size - 2 * moduleSize, innerRadius))
    }
}

/** Square rect of side [size] at ([left], [top]). */
private fun square(left: Float, top: Float, size: Float): Rect = Rect(left, top, left + size, top + size)

/** Square rounded rect of side [size] at ([left], [top]) with uniform [radius]. */
private fun roundSquare(left: Float, top: Float, size: Float, radius: Float): RoundRect =
    RoundRect(square(left, top, size), CornerRadius(radius))

/** This radius, or zero when [flat] is true. */
private fun CornerRadius.unless(flat: Boolean): CornerRadius = if (flat) CornerRadius.Zero else this
