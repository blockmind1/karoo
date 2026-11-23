package es.lapnp.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapNpCalculatorTest {

    @Test
    fun constantPower_npEqualsAverage() {
        val c = LapNpCalculator()
        repeat(300) { c.add(250.0) }
        assertEquals(250.0, c.normalizedPower()!!, 0.001)
        assertEquals(250.0, c.average()!!, 0.001)
    }

    @Test
    fun firstThirtySeconds_showsAverage() {
        val c = LapNpCalculator()
        repeat(29) { c.add(200.0) }
        assertNull(c.normalizedPower())
        assertFalse(c.isUsingNp())
        assertEquals(200.0, c.displayValue()!!, 0.001)
        c.add(200.0)
        assertTrue(c.isUsingNp())
    }

    @Test
    fun variablePower_npAboveAverage() {
        // 60 s a 400 W + 60 s a 100 W, repetido: media 250, NP claramente mayor
        val c = LapNpCalculator()
        repeat(5) {
            repeat(60) { c.add(400.0) }
            repeat(60) { c.add(100.0) }
        }
        assertEquals(250.0, c.average()!!, 0.001)
        assertTrue(c.normalizedPower()!! > 290.0)
    }

    @Test
    fun resetClearsEverything() {
        val c = LapNpCalculator()
        repeat(100) { c.add(300.0) }
        c.reset()
        assertNull(c.average())
        assertEquals(0, c.elapsedSeconds)
        repeat(40) { c.add(150.0) }
        assertEquals(150.0, c.normalizedPower()!!, 0.001)
    }

    @Test
    fun zoneClassification() {
        val t = PowerTarget(250, 270)
        assertEquals(Zone.WARMING_UP, TargetZone.classify(260.0, t, 5))
        assertEquals(Zone.IN_RANGE, TargetZone.classify(260.0, t, 60))
        assertEquals(Zone.BELOW, TargetZone.classify(240.0, t, 60))
        assertEquals(Zone.ABOVE, TargetZone.classify(280.0, t, 60))
        assertEquals(Zone.NO_TARGET, TargetZone.classify(280.0, null, 60))
        assertEquals(-10, TargetZone.deviation(240.0, t))
        assertEquals(10, TargetZone.deviation(280.0, t))
        assertEquals(0, TargetZone.deviation(260.0, t))
    }

    @Test
    fun targetBuilding() {
        assertEquals(PowerTarget(250, 270), TargetZone.build(250.0, 270.0, 260.0, 280))
        // Solo valor central → ±5 %
        assertEquals(PowerTarget(190, 210), TargetZone.build(null, null, 200.0, 280))
        // Fracciones de FTP → vatios
        assertEquals(PowerTarget(252, 280), TargetZone.build(0.9, 1.0, null, 280))
        // Sin objetivo
        assertNull(TargetZone.build(null, null, null, 280))
        assertNull(TargetZone.build(0.0, 0.0, 0.0, 280))
    }
}
