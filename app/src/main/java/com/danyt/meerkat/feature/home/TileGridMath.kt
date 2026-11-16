package com.danyt.meerkat.feature.home

/**
 * The geometry of the dashboard grid, in pixels. Pure arithmetic with no UI, so it can be unit tested.
 *
 * Tiles are square, [tilePx] wide, laid out left to right and top to bottom in [columns] columns with [gapPx]
 * between them.
 */
internal class TileGridMath(val columns: Int, val tilePx: Int, val gapPx: Int) {

    /** Distance from one tile's left or top edge to the next one's. */
    val stepPx: Int = tilePx + gapPx

    /** Left edge of the slot at [index]. */
    fun slotX(index: Int): Int = (index % columns) * stepPx

    /** Top edge of the slot at [index]. */
    fun slotY(index: Int): Int = (index / columns) * stepPx

    /** Height of a grid holding [count] tiles. Zero when empty. */
    fun heightPx(count: Int): Int {
        val rows = (count + columns - 1) / columns
        return (rows * stepPx - gapPx).coerceAtLeast(0)
    }

    /**
     * The slot a dragged tile should move to.
     *
     * @param from the tile's current slot.
     * @param dragX how far the tile has been dragged right of that slot.
     * @param dragY how far it has been dragged below that slot.
     * @param count number of tiles in the grid.
     * @return the slot under the tile's center, clamped to the grid and to the existing tiles.
     */
    fun targetIndex(from: Int, dragX: Int, dragY: Int, count: Int): Int {
        if (count <= 0) return 0
        val centerX = slotX(from) + dragX + tilePx / 2
        val centerY = slotY(from) + dragY + tilePx / 2
        val column = Math.floorDiv(centerX, stepPx).coerceIn(0, columns - 1)
        val row = Math.floorDiv(centerY, stepPx).coerceAtLeast(0)
        return (row * columns + column).coerceIn(0, count - 1)
    }

    companion object {
        private const val MIN_COLUMNS = 2
        private const val MAX_COLUMNS = 6

        /** Builds the grid for a container [widthPx] wide, fitting as many columns as [minTilePx] allows. */
        fun fit(widthPx: Int, minTilePx: Int, gapPx: Int): TileGridMath {
            val columns = ((widthPx + gapPx) / (minTilePx + gapPx).coerceAtLeast(1)).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            val tilePx = ((widthPx - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
            return TileGridMath(columns, tilePx, gapPx)
        }
    }
}

/** Returns a copy of the list with the item at [from] moved to position [to]. Out-of-range moves change nothing. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
