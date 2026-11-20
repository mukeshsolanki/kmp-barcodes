package com.mukeshsolanki.barcodes

/** Decoded image pixels in ARGB_8888 order, row-major. */
internal class ArgbImage(
    /** Packed ARGB pixels, `width * height` long. */
    val pixels: IntArray,
    /** Width in pixels. */
    val width: Int,
    /** Height in pixels. */
    val height: Int,
)
