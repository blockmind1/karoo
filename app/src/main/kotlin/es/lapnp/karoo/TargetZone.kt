package es.lapnp.karoo

import kotlin.math.roundToInt

/** Estado de color del campo. Mismo criterio que el campo nativo del Karoo. */
enum class Zone {
    /** Sin objetivo de potencia (no hay entreno o el intervalo no es de potencia). */
    NO_TARGET,
    /** Primeros segundos del intervalo: el valor aún no es representativo. */
    WARMING_UP,
    BELOW,
    IN_RANGE,
    ABOVE,
}

data class PowerTarget(val min: Int, val max: Int)

object TargetZone {

    /** Segundos al inicio de cada intervalo en los que no se colorea. */
    const val GRACE_SECONDS = 10

    /** Rango aplicado si el entreno solo trae un valor único (el Karoo usa ±5 %). */
    private const val SINGLE_VALUE_TOLERANCE = 0.05

    fun classify(value: Double?, target: PowerTarget?, elapsedSeconds: Int): Zone = when {
        target == null -> Zone.NO_TARGET
        value == null || elapsedSeconds < GRACE_SECONDS -> Zone.WARMING_UP
        value < target.min -> Zone.BELOW
        value > target.max -> Zone.ABOVE
        else -> Zone.IN_RANGE
    }

    /** Vatios fuera del rango: negativo por debajo, positivo por encima, 0 dentro. */
    fun deviation(value: Double?, target: PowerTarget?): Int? {
        if (value == null || target == null) return null
        val v = value.roundToInt()
        return when {
            v < target.min -> v - target.min
            v > target.max -> v - target.max
            else -> 0
        }
    }

    /**
     * Construye el objetivo a partir de los campos del Karoo.
     * - Si trae mínimo y máximo, se usan tal cual.
     * - Si solo trae un valor, se aplica ±5 %.
     * - Si los valores parecen fracciones de FTP (≤ 5) se convierten con el FTP del perfil.
     */
    fun build(min: Double?, max: Double?, value: Double?, ftp: Int?): PowerTarget? {
        fun toWatts(x: Double?): Double? {
            if (x == null || !x.isFinite() || x <= 0.0) return null
            return if (x <= 5.0) {
                if (ftp != null && ftp > 0) x * ftp else null
            } else x
        }

        val lo = toWatts(min)
        val hi = toWatts(max)
        if (lo != null && hi != null && hi > lo) {
            return PowerTarget(lo.roundToInt(), hi.roundToInt())
        }
        // Rango de anchura cero o solo valor central: ±5 %.
        val v = toWatts(value) ?: lo ?: return null
        return PowerTarget(
            (v * (1 - SINGLE_VALUE_TOLERANCE)).roundToInt(),
            (v * (1 + SINGLE_VALUE_TOLERANCE)).roundToInt(),
        )
    }
}
