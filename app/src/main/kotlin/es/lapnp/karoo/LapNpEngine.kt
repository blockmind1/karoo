package es.lapnp.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Lap
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Lo que se pinta en el campo. */
data class LapNpState(
    val value: Int?,          // NP de la vuelta (o media si < 30 s)
    val isNp: Boolean,
    val average: Int?,
    val elapsedSeconds: Int,
    val target: PowerTarget?,
    val zone: Zone,
    val deviation: Int?,      // vatios fuera del rango (0 = dentro)
) {
    companion object {
        val EMPTY = LapNpState(null, false, null, 0, null, Zone.NO_TARGET, null)
    }
}

/**
 * Una única instancia por extensión: muestrea la potencia a 1 Hz mientras se graba,
 * reinicia el cálculo en cada vuelta o paso del entreno y publica el estado.
 */
class LapNpEngine(
    private val karoo: KarooSystemService,
    private val scope: CoroutineScope,
) {
    private val calc = LapNpCalculator()
    private val lock = Any()

    private val _state = MutableStateFlow(LapNpState.EMPTY)
    val state: StateFlow<LapNpState> = _state.asStateFlow()

    @Volatile private var latestPower: Double? = null
    @Volatile private var recording = false
    @Volatile private var target: PowerTarget? = null
    @Volatile private var ftp: Int? = null
    @Volatile private var currentStep: Int? = null

    fun start() {
        // Potencia instantánea
        scope.launch {
            karoo.streamDataFlow(DataType.Type.POWER).collect { s ->
                val w = s.field(DataType.Field.POWER)
                    ?: (s as? StreamState.Streaming)?.dataPoint?.singleValue
                // Si el sensor se pierde, el stream pasa a Searching/NotAvailable → null:
                // esos segundos no se cuentan (ni como cero).
                latestPower = w
            }
        }

        // Estado de la grabación: solo se acumula mientras se graba; al terminar se reinicia.
        scope.launch {
            karoo.consumerFlow<RideState>().collect { rs ->
                recording = rs is RideState.Recording
                if (rs is RideState.Idle) resetLap()
            }
        }

        // Vuelta nueva (manual, automática o creada por el entreno)
        scope.launch {
            karoo.consumerFlow<Lap>().collect { resetLap() }
        }

        // Paso del entreno: por si un intervalo cambia sin generar vuelta
        scope.launch {
            karoo.streamDataFlow(DataType.Type.WORKOUT_INTERVAL_COUNT).collect { s ->
                val step = s.field(DataType.Field.WORKOUT_CURRENT_STEP)?.roundToInt()
                if (step != null && step != currentStep) {
                    if (currentStep != null) resetLap()
                    currentStep = step
                }
            }
        }

        // FTP del perfil, por si el objetivo llega como fracción de FTP
        scope.launch {
            karoo.consumerFlow<UserProfile>().collect { ftp = it.ftp }
        }

        // Objetivo de potencia del intervalo actual
        scope.launch {
            karoo.streamDataFlow(DataType.Type.WORKOUT_POWER_TARGET).collect { s ->
                target = TargetZone.build(
                    min = s.field(DataType.Field.WORKOUT_TARGET_MIN_VALUE),
                    max = s.field(DataType.Field.WORKOUT_TARGET_MAX_VALUE),
                    value = s.field(DataType.Field.WORKOUT_TARGET_VALUE),
                    ftp = ftp,
                )
                publish()
            }
        }

        // Reloj de 1 Hz: una muestra por segundo, como exige el cálculo de NP
        scope.launch {
            var next = System.currentTimeMillis()
            while (isActive) {
                next += 1000
                val w = latestPower
                if (recording && w != null) {
                    synchronized(lock) { calc.add(w) }
                }
                publish()
                delay((next - System.currentTimeMillis()).coerceAtLeast(0))
            }
        }
    }

    private fun resetLap() {
        synchronized(lock) { calc.reset() }
        publish()
    }

    private fun publish() {
        val t = target
        val (value, isNp, avg, elapsed) = synchronized(lock) {
            Quad(calc.displayValue(), calc.isUsingNp(), calc.average(), calc.elapsedSeconds)
        }
        _state.value = LapNpState(
            value = value?.roundToInt(),
            isNp = isNp,
            average = avg?.roundToInt(),
            elapsedSeconds = elapsed,
            target = t,
            zone = TargetZone.classify(value, t, elapsed),
            deviation = TargetZone.deviation(value, t),
        )
    }

    private data class Quad(val a: Double?, val b: Boolean, val c: Double?, val d: Int)
}
