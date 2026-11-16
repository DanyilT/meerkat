package com.danyt.meerkat.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TileGridMathTest {

    // Two columns of 100 px tiles with a 10 px gap: slots start at 0 and 110 on each axis.
    private val grid = TileGridMath(columns = 2, tilePx = 100, gapPx = 10)

    @Test
    fun `slots run left to right, then top to bottom`() {
        assertEquals(0 to 0, grid.slotX(0) to grid.slotY(0))
        assertEquals(110 to 0, grid.slotX(1) to grid.slotY(1))
        assertEquals(0 to 110, grid.slotX(2) to grid.slotY(2))
        assertEquals(110 to 220, grid.slotX(5) to grid.slotY(5))
    }

    @Test
    fun `height covers every row without a trailing gap`() {
        assertEquals(0, grid.heightPx(0))
        assertEquals(100, grid.heightPx(1))
        assertEquals(100, grid.heightPx(2))
        assertEquals(210, grid.heightPx(3))
        assertEquals(320, grid.heightPx(6))
    }

    @Test
    fun `a tile that has not moved stays in its slot`() {
        for (index in 0 until 6) assertEquals(index, grid.targetIndex(index, 0, 0, count = 6))
    }

    @Test
    fun `a small drag does not change the slot`() {
        assertEquals(0, grid.targetIndex(from = 0, dragX = 40, dragY = 40, count = 6))
    }

    @Test
    fun `the target is the slot under the tile's center`() {
        // Center crosses into the second column once dragged more than half a step.
        assertEquals(1, grid.targetIndex(from = 0, dragX = 70, dragY = 0, count = 6))
        assertEquals(2, grid.targetIndex(from = 0, dragX = 0, dragY = 70, count = 6))
        assertEquals(3, grid.targetIndex(from = 0, dragX = 110, dragY = 110, count = 6))
        assertEquals(0, grid.targetIndex(from = 3, dragX = -110, dragY = -110, count = 6))
    }

    @Test
    fun `dragging outside the grid clamps to the nearest slot`() {
        assertEquals(0, grid.targetIndex(from = 0, dragX = -500, dragY = -500, count = 6))
        assertEquals(1, grid.targetIndex(from = 0, dragX = 5_000, dragY = 0, count = 6))
        assertEquals(5, grid.targetIndex(from = 0, dragX = 5_000, dragY = 5_000, count = 6))
    }

    @Test
    fun `the target never points past the last tile`() {
        // Five tiles: the bottom-right slot is empty, so dropping there means "last".
        assertEquals(4, grid.targetIndex(from = 0, dragX = 110, dragY = 220, count = 5))
        assertEquals(0, grid.targetIndex(from = 0, dragX = 300, dragY = 300, count = 1))
        assertEquals(0, grid.targetIndex(from = 0, dragX = 0, dragY = 0, count = 0))
    }

    @Test
    fun `fit uses as many columns as the minimum tile width allows, within two to six`() {
        assertEquals(2, TileGridMath.fit(widthPx = 300, minTilePx = 170, gapPx = 12).columns)
        assertEquals(2, TileGridMath.fit(widthPx = 100, minTilePx = 170, gapPx = 12).columns)
        assertEquals(4, TileGridMath.fit(widthPx = 800, minTilePx = 170, gapPx = 12).columns)
        assertEquals(6, TileGridMath.fit(widthPx = 5_000, minTilePx = 170, gapPx = 12).columns)
    }

    @Test
    fun `fit sizes tiles to fill the width exactly`() {
        val fitted = TileGridMath.fit(widthPx = 352, minTilePx = 170, gapPx = 12)
        assertEquals(2, fitted.columns)
        assertEquals(170, fitted.tilePx)
        assertEquals(352, fitted.columns * fitted.tilePx + (fitted.columns - 1) * fitted.gapPx)
    }

    @Test
    fun `moved shifts the items in between`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), list.moved(3, 0))
    }

    @Test
    fun `moved ignores no-op and out-of-range moves`() {
        val list = listOf("a", "b", "c")
        assertSame(list, list.moved(1, 1))
        assertSame(list, list.moved(-1, 2))
        assertSame(list, list.moved(0, 3))
    }
}
