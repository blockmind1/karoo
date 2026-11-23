package es.lapnp.karoo

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Campo numérico simple: NP de la vuelta (media los primeros 30 s). */
class LapNpNumericDataType(
    extension: String,
    private val engine: LapNpEngine,
) : DataTypeImpl(extension, TYPE_ID) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            engine.state.collect { s ->
                val v = s.value
                emitter.onNext(
                    if (v == null) StreamState.Searching
                    else StreamState.Streaming(
                        DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v.toDouble())),
                    ),
                )
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    companion object { const val TYPE_ID = "lap-np" }
}

/** Campo gráfico: NP de la vuelta con fondo verde / azul / rojo según el objetivo del intervalo. */
class LapNpZoneDataType(
    extension: String,
    private val engine: LapNpEngine,
) : DataTypeImpl(extension, TYPE_ID) {

    override fun startStream(emitter: Emitter<StreamState>) {
        // También emite el valor para que el campo sea utilizable por otros tipos.
        val job = CoroutineScope(Dispatchers.IO).launch {
            engine.state.collect { s ->
                val v = s.value ?: return@collect
                emitter.onNext(
                    StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val job = CoroutineScope(Dispatchers.IO).launch {
            val source: Flow<LapNpState> = if (config.preview) flowOf(PREVIEW_STATE) else engine.state
            // El Karoo descarta vistas enviadas a más de 1 Hz: se conserva solo el último estado
            // y se espera 1 s entre actualizaciones para que ningún cambio de color se pierda.
            source.distinctUntilChanged().conflate().collect { s ->
                emitter.updateView(render(context, config, s))
                delay(1000)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun render(context: Context, config: ViewConfig, s: LapNpState): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.view_lap_np)

        val bg = when (s.zone) {
            Zone.IN_RANGE -> GREEN
            Zone.BELOW -> BLUE
            Zone.ABOVE -> RED
            Zone.WARMING_UP -> GREY
            Zone.NO_TARGET -> DARK
        }
        rv.setInt(R.id.root, "setBackgroundColor", bg)

        val gravity = when (config.alignment) {
            ViewConfig.Alignment.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            ViewConfig.Alignment.CENTER -> Gravity.CENTER
            ViewConfig.Alignment.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        rv.setInt(R.id.root, "setGravity", gravity)

        // Cabecera: qué se está mostrando
        val label = if (s.isNp) "NP VUELTA" else "MEDIA VUELTA"
        val arrow = when (s.zone) {
            Zone.BELOW -> " ▲"
            Zone.ABOVE -> " ▼"
            else -> ""
        }
        rv.setTextViewText(R.id.label, label + arrow)

        // Valor grande
        rv.setTextViewText(R.id.value, s.value?.toString() ?: "--")
        rv.setTextViewTextSize(R.id.value, TypedValue.COMPLEX_UNIT_SP, config.textSize.toFloat())

        // Línea de detalle: rango objetivo y desviación
        val detail = when {
            s.target == null -> s.average?.let { "media $it W" } ?: ""
            else -> {
                val range = "${s.target.min}–${s.target.max}"
                val dev = when (val d = s.deviation) {
                    null, 0 -> ""
                    else -> "  ${if (d > 0) "+" else ""}$d"
                }
                range + dev
            }
        }
        rv.setTextViewText(R.id.detail, detail)

        val small = (config.textSize * 0.32f).coerceIn(11f, 20f)
        rv.setTextViewTextSize(R.id.label, TypedValue.COMPLEX_UNIT_SP, small)
        rv.setTextViewTextSize(R.id.detail, TypedValue.COMPLEX_UNIT_SP, small)
        return rv
    }

    companion object {
        const val TYPE_ID = "lap-np-zone"

        private val GREEN = Color.parseColor("#2E7D32")
        private val BLUE = Color.parseColor("#1565C0")
        private val RED = Color.parseColor("#C62828")
        private val GREY = Color.parseColor("#616161")
        private val DARK = Color.parseColor("#212121")

        private val PREVIEW_STATE = LapNpState(
            value = 262, isNp = true, average = 255, elapsedSeconds = 120,
            target = PowerTarget(250, 270), zone = Zone.IN_RANGE, deviation = 0,
        )
    }
}
