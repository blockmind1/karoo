package es.lapnp.karoo

import kotlin.math.pow

/**
 * Potencia normalizada (NP) de la vuelta en curso, calculada a partir de muestras de 1 s.
 *
 * Algoritmo estándar (Coggan):
 *  1. media móvil de 30 s de la potencia,
 *  2. cada valor de esa media elevado a la 4ª potencia,
 *  3. media de esos valores,
 *  4. raíz cuarta.
 *
 * Hasta tener 30 muestras no existe NP; mientras tanto se usa la potencia media.
 * Sin dependencias de Android para poder probarlo con JUnit.
 */
class LapNpCalculator(private val windowSeconds: Int = 30) {

    private val window = DoubleArray(windowSeconds)
    private var windowSum = 0.0
    private var count = 0          // segundos acumulados en la vuelta
    private var totalSum = 0.0     // para la potencia media
    private var fourthPowerSum = 0.0
    private var fourthPowerCount = 0

    /** Segundos registrados en la vuelta actual. */
    val elapsedSeconds: Int get() = count

    /** Añade una muestra de 1 segundo (vatios). Los ceros por dejar de pedalear cuentan. */
    fun add(watts: Double) {
        val w = if (watts.isFinite() && watts > 0.0) watts else 0.0
        val slot = count % windowSeconds
        if (count >= windowSeconds) windowSum -= window[slot]
        window[slot] = w
        windowSum += w
        count++
        totalSum += w

        if (count >= windowSeconds) {
            val rolling = windowSum / windowSeconds
            fourthPowerSum += rolling.pow(4)
            fourthPowerCount++
        }
    }

    /** Potencia media de la vuelta, o null si aún no hay muestras. */
    fun average(): Double? = if (count == 0) null else totalSum / count

    /** NP de la vuelta, o null si hay menos de [windowSeconds] muestras. */
    fun normalizedPower(): Double? =
        if (fourthPowerCount == 0) null else (fourthPowerSum / fourthPowerCount).pow(0.25)

    /** NP si ya existe; si no, la media. */
    fun displayValue(): Double? = normalizedPower() ?: average()

    fun isUsingNp(): Boolean = fourthPowerCount > 0

    fun reset() {
        window.fill(0.0)
        windowSum = 0.0
        count = 0
        totalSum = 0.0
        fourthPowerSum = 0.0
        fourthPowerCount = 0
    }
}
