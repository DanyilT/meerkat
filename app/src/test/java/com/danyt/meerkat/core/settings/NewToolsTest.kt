package com.danyt.meerkat.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewToolsTest {

    @Test
    fun `a new install marks nothing as new`() {
        val update = reconcileNewTools(listOf("lan", "ports"), layout = null, known = null, fresh = emptySet())
        assertEquals(NewToolsUpdate(null, setOf("lan", "ports"), emptySet()), update)
    }

    @Test
    fun `a new tool is added to the end of a customized dashboard and marked new`() {
        val update = reconcileNewTools(
            built = listOf("lan", "ports", "hotspot"),
            layout = listOf("ports", "ssh", "lan"),
            known = setOf("lan", "ports"),
            fresh = emptySet(),
        )
        val expected = NewToolsUpdate(
            layout = listOf("ports", "ssh", "lan", "hotspot"),
            known = setOf("lan", "ports", "hotspot"),
            fresh = setOf("hotspot"),
        )
        assertEquals(expected, update)
    }

    @Test
    fun `a tool that was already on the dashboard as Soon gets the badge but is not added twice`() {
        val update = reconcileNewTools(
            built = listOf("lan", "ssh"),
            layout = listOf("ssh", "lan"),
            known = setOf("lan"),
            fresh = emptySet(),
        )
        assertEquals(NewToolsUpdate(listOf("ssh", "lan"), setOf("lan", "ssh"), setOf("ssh")), update)
    }

    @Test
    fun `with the default dashboard new tools show anyway and only get the badge`() {
        val update =
            reconcileNewTools(listOf("lan", "hotspot"), layout = null, known = setOf("lan"), fresh = emptySet())
        assertEquals(NewToolsUpdate(null, setOf("lan", "hotspot"), setOf("hotspot")), update)
    }

    @Test
    fun `a removed tool is never put back`() {
        val known = setOf("lan", "ports")
        assertNull(reconcileNewTools(listOf("lan", "ports"), layout = listOf("lan"), known = known, fresh = emptySet()))
    }

    @Test
    fun `an update from a version without tracking treats the current dashboard as known`() {
        val update = reconcileNewTools(
            built = listOf("lan", "ports", "hotspot"),
            layout = listOf("lan", "ports", "ssh"),
            known = null,
            fresh = emptySet(),
        )
        val expected = NewToolsUpdate(
            layout = listOf("lan", "ports", "ssh", "hotspot"),
            known = setOf("lan", "ports", "hotspot"),
            fresh = setOf("hotspot"),
        )
        assertEquals(expected, update)
    }
}
