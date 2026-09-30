package dev.dewy

import dev.dewy.boxes.powerOffNote
import dev.dewy.net.Box
import dev.dewy.net.PowerOff
import dev.dewy.net.wireJson
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerOffTest {
    @Test
    fun theSwitchSaysWhatItDoes() {
        assertEquals("Shuts the machine down a minute after its last task ends", powerOffNote(PowerOff(can = true), 0, 0, 0))
        assertEquals("Waiting for 2 tasks to finish", powerOffNote(PowerOff(true, armed = true), 2, 0, 0))
        assertEquals("Waiting for 1 task and 1 terminal session to finish", powerOffNote(PowerOff(true, armed = true), 1, 1, 0))
        assertEquals("Once nothing runs here", powerOffNote(PowerOff(true, armed = true), 0, 0, 0))
        assertEquals("Turning off in 42 s", powerOffNote(PowerOff(true, true, at = 60_000), 0, 0, 18_000))
        assertEquals("systemctl poweroff failed on the box.", powerOffNote(PowerOff(true, error = "systemctl poweroff failed on the box."), 0, 0, 0))
    }

    @Test
    fun aBoxFromBeforeCannotTurnOff() {
        assertEquals(PowerOff(), wireJson.decodeFromString<Box>("""{"name":"old"}""").poweroff)
        val armed = wireJson.decodeFromString<Box>("""{"name":"pc","poweroff":{"can":true,"armed":true,"at":955004}}""")
        assertEquals(955004L, armed.poweroff.at)
    }
}
