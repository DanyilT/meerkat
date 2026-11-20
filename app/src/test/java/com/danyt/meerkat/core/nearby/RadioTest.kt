package com.danyt.meerkat.core.nearby

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTest {

    @Test
    fun channelsAcrossBands() {
        assertEquals(1, wifiChannel(2412))
        assertEquals(13, wifiChannel(2472))
        assertEquals(14, wifiChannel(2484))
        assertEquals(36, wifiChannel(5180))
        assertEquals(165, wifiChannel(5825))
        assertEquals(1, wifiChannel(5955))
        assertEquals(233, wifiChannel(7115))
        assertEquals(1, wifiChannel(58_320))
        assertEquals(2, wifiChannel(60_480))
        assertNull(wifiChannel(2413))
        assertNull(wifiChannel(1000))
    }

    @Test
    fun bands() {
        assertEquals(WifiBand.Band2Ghz, wifiBand(2437))
        assertEquals(WifiBand.Band5Ghz, wifiBand(5500))
        assertEquals(WifiBand.Band6Ghz, wifiBand(6115))
        assertEquals(WifiBand.Band60Ghz, wifiBand(60_480))
        assertNull(wifiBand(900))
    }

    @Test
    fun securityFromCapabilities() {
        assertEquals(listOf(WifiSecurity.Wpa2), wifiSecurity("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(listOf(WifiSecurity.Wpa3), wifiSecurity("[RSN-SAE-CCMP][ESS][MFPR]"))
        assertEquals(listOf(WifiSecurity.Wpa3, WifiSecurity.Wpa2), wifiSecurity("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(listOf(WifiSecurity.Wpa2, WifiSecurity.Wpa), wifiSecurity("[WPA-PSK-TKIP+CCMP][WPA2-PSK-CCMP]"))
        assertEquals(listOf(WifiSecurity.Wpa2Enterprise), wifiSecurity("[RSN-EAP/SHA1-CCMP][ESS]"))
        assertEquals(listOf(WifiSecurity.WpaEnterprise), wifiSecurity("[WPA-EAP-TKIP][ESS]"))
        assertEquals(listOf(WifiSecurity.Wpa3Enterprise), wifiSecurity("[RSN-EAP_SUITE_B_192-GCMP-256][ESS]"))
        assertEquals(listOf(WifiSecurity.Wpa3), wifiSecurity("[RSN-FT/SAE-CCMP][ESS]"))
        assertEquals(listOf(WifiSecurity.Owe), wifiSecurity("[RSN-OWE-CCMP][ESS]"))
        assertEquals(listOf(WifiSecurity.Wep), wifiSecurity("[WEP][ESS]"))
        assertEquals(listOf(WifiSecurity.Open), wifiSecurity("[ESS]"))
        assertEquals(listOf(WifiSecurity.Open), wifiSecurity(""))
    }

    @Test
    fun signalLevels() {
        assertEquals(4, signalLevel(-40))
        assertEquals(3, signalLevel(-60))
        assertEquals(2, signalLevel(-70))
        assertEquals(1, signalLevel(-85))
        assertEquals(0, signalLevel(-95))
    }

    @Test
    fun randomMacs() {
        assertTrue(isRandomMac("DA:A1:19:00:11:22"))
        assertTrue(isRandomMac("02:00:00:00:00:00"))
        assertFalse(isRandomMac("00:1A:2B:3C:4D:5E"))
        assertFalse(isRandomMac("garbage"))
    }

    @Test
    fun cleanNameDropsControlCharacters() {
        assertEquals("Home Wi-Fi", cleanName(" Home\u0000 Wi-Fi\n"))
        assertEquals(64, cleanName("x".repeat(100)).length)
    }

    @Test
    fun shortUuids() {
        assertEquals(0x180F, shortUuid(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")))
        assertEquals(0xFE2C, shortUuid(UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")))
        assertNull(shortUuid(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
        assertNull(shortUuid(UUID.fromString("0001180f-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun companyNames() {
        assertEquals("Apple", companyName(0x004C))
        assertEquals("0x1234", companyName(0x1234))
        assertEquals("0x180F", shortUuidText(0x180F))
    }

    private fun network(frequency: Int, width: Int = 20, center: Int? = null) = WifiNetwork(
        ssid = "n",
        bssid = "00:00:00:00:00:00",
        dbm = -50,
        frequency = frequency,
        channelWidth = width,
        centerFrequency = center,
        security = emptyList(),
        standard = null,
        passpoint = false,
        seenAt = 0,
    )

    @Test
    fun crowdingCountsOverlap() {
        val crowding = channelCrowding(listOf(network(2437), network(2437), network(2412)), WifiBand.Band2Ghz).toMap()
        // Channel 6 and the channels within 10 MHz of it.
        assertEquals(2, crowding[6])
        assertEquals(2, crowding[5])
        assertEquals(2, crowding[7])
        assertEquals(0, crowding[9])
        assertEquals(1, crowding[1])
        assertNull(crowding[14])
    }

    @Test
    fun wideChannelsUseTheirCentre() {
        // An 80 MHz network on primary channel 36, centred on channel 42 (5210 MHz), covers 36 to 48.
        val crowding = channelCrowding(listOf(network(5180, width = 80, center = 5210)), WifiBand.Band5Ghz).toMap()
        assertEquals(listOf(1, 1, 1, 1, 0), listOf(36, 40, 44, 48, 52).map { crowding[it] })
        assertEquals(5180, wifiChannelFrequency(WifiBand.Band5Ghz, 36))
        assertEquals(2484, wifiChannelFrequency(WifiBand.Band2Ghz, 14))
    }

    @Test
    fun distances() {
        assertEquals(1f, bluetoothDistanceMeters(-59), 0.01f)
        assertEquals(10f, bluetoothDistanceMeters(-84), 0.1f)
        // A device that says it sends at 0 dBm is expected at -41 dBm at one metre.
        assertEquals(1f, bluetoothDistanceMeters(-41, txPower = 0), 0.01f)
        assertEquals(100f, bluetoothDistanceMeters(-200))
        val lte = CellTower(CellTech.Lte, registered = true, timingAdvance = 10)
        assertEquals(780, lte.distanceMeters())
        assertEquals(1106, CellTower(CellTech.Gsm, registered = true, timingAdvance = 2).distanceMeters())
        // 5G reports microseconds: 150 m each.
        assertEquals(450, CellTower(CellTech.Nr, registered = true, timingAdvance = 3).distanceMeters())
        assertNull(CellTower(CellTech.Wcdma, registered = true, timingAdvance = 3).distanceMeters())
        assertNull(CellTower(CellTech.Lte, registered = true).distanceMeters())
    }

    @Test
    fun wifiDistance() {
        // -50 dBm on channel 6 is about 3 m; every 6 dB weaker roughly doubles it.
        assertEquals(3.1f, wifiDistanceMeters(-50, 2437), 0.1f)
        assertEquals(6.2f, wifiDistanceMeters(-56, 2437), 0.2f)
        // The same signal at 5 GHz means the access point is closer.
        assertTrue(wifiDistanceMeters(-50, 5180) < wifiDistanceMeters(-50, 2437))
        assertEquals(0f, wifiDistanceMeters(-50, 0))
    }
}
