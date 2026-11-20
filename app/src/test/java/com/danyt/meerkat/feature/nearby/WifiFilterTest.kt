package com.danyt.meerkat.feature.nearby

import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.WifiSecurity
import com.danyt.meerkat.core.nearby.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiFilterTest {

    private val home = network("Home", "AA:BB:CC:00:00:01", 2437, listOf(WifiSecurity.Wpa2), WifiStandard.N)
    private val office =
        network("Office", "AA:BB:CC:00:00:02", 5180, listOf(WifiSecurity.Wpa2Enterprise), WifiStandard.Ac)
    private val new = network(null, "DE:AD:00:00:00:03", 6115, listOf(WifiSecurity.Wpa3), WifiStandard.Ax)

    private fun network(
        ssid: String?,
        bssid: String,
        frequency: Int,
        security: List<WifiSecurity>,
        standard: WifiStandard,
    ) = WifiNetwork(ssid, bssid, -60, frequency, 20, null, security, standard, false, 0)

    private fun WifiFilter.pick() = listOf(home, office, new).filter(::matches)

    @Test
    fun emptyFilterKeepsEverything() {
        assertFalse(WifiFilter().isActive)
        assertEquals(3, WifiFilter().pick().size)
    }

    @Test
    fun eachCriterion() {
        assertEquals(listOf(home), WifiFilter(query = "hom").pick())
        assertEquals(listOf(new), WifiFilter(query = "de:ad").pick())
        assertEquals(listOf(office), WifiFilter(band = WifiBand.Band5Ghz).pick())
        assertEquals(listOf(home), WifiFilter(channels = setOf(2437)).pick())
        // Personal and Enterprise count as the same generation.
        assertEquals(listOf(home, office), WifiFilter(security = setOf(SecurityKind.Wpa2)).pick())
        assertEquals(listOf(new), WifiFilter(standards = setOf(StandardKind.Wifi6E)).pick())
    }

    @Test
    fun criteriaCombine() {
        val filter = WifiFilter(query = "o", security = setOf(SecurityKind.Wpa2), band = WifiBand.Band2Ghz)
        assertEquals(3, filter.count)
        assertTrue(filter.isActive)
        assertEquals(listOf(home), filter.pick())
    }

    @Test
    fun personalAndEnterprise() {
        assertEquals(listOf(office), WifiFilter(securityType = SecurityType.Enterprise).pick())
        assertEquals(listOf(home, new), WifiFilter(securityType = SecurityType.Personal).pick())
        val wpa2Personal = WifiFilter(security = setOf(SecurityKind.Wpa2), securityType = SecurityType.Personal)
        assertEquals(listOf(home), wpa2Personal.pick())
    }

    @Test
    fun savedAndRead() {
        val filter = WifiFilter(
            query = "a=b",
            band = WifiBand.Band5Ghz,
            channels = setOf(5180, 5200),
            security = setOf(SecurityKind.Wpa3, SecurityKind.Wpa2),
            securityType = SecurityType.Enterprise,
            standards = setOf(StandardKind.Wifi6E, StandardKind.Wifi7),
            applyToChart = true,
        )
        assertEquals(filter, WifiFilter.decode(filter.encode()))
        assertEquals(WifiFilter(), WifiFilter.decode(mapOf("band" to "Nowhere", "channel" to "x")))
        assertTrue(WifiFilter().encode().isEmpty())
    }

    @Test
    fun severalSecurityKinds() {
        // WPA2 or WPA3: the old Personal network and the new one, not the 5 GHz Enterprise one only by type.
        val filter = WifiFilter(security = setOf(SecurityKind.Wpa2, SecurityKind.Wpa3))
        assertEquals(listOf(home, office, new), filter.pick())
        assertEquals(listOf(new), WifiFilter(security = setOf(SecurityKind.Wpa3)).pick())
    }

    @Test
    fun severalChannelsAndStandards() {
        assertEquals(listOf(home, office), WifiFilter(channels = setOf(2437, 5180)).pick())
        assertEquals(listOf(home, office), WifiFilter(standards = setOf(StandardKind.Wifi4, StandardKind.Wifi5)).pick())
    }
}
