package com.example

import com.example.data.model.GameApp
import com.example.data.model.ServerStatus
import com.example.data.model.StreamConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testStreamConfigResolutionAndBitrate() {
        val config1080p = StreamConfig(width = 1920, height = 1080, fps = 60, bitrateMbps = 12)
        assertEquals("1920x1080 @ 60fps", config1080p.resolutionLabel())
        assertEquals(12_000_000, config1080p.bitrateBps)

        val config1610 = StreamConfig(width = 1920, height = 1200, fps = 60, bitrateMbps = 15)
        assertEquals("1920x1200 @ 60fps", config1610.resolutionLabel())
        assertEquals(15_000_000, config1610.bitrateBps)
    }

    @Test
    fun testServerStatusUrl() {
        val status = ServerStatus(
            isRunning = true,
            ipAddress = "192.168.1.100",
            port = 8080
        )
        assertEquals("http://192.168.1.100:8080", status.serverUrl)
    }

    @Test
    fun testGameAppModel() {
        val app = GameApp(
            packageName = "org.ppsspp.ppsspp",
            appName = "PPSSPP",
            isEmulator = true,
            category = "Emulator"
        )
        assertTrue(app.isEmulator)
        assertEquals("PPSSPP", app.appName)
    }
}
