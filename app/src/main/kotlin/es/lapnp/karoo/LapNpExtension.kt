package es.lapnp.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class LapNpExtension : KarooExtension(EXTENSION_ID, "1.0") {

    private lateinit var karooSystem: KarooSystemService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: LapNpEngine

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            LapNpZoneDataType(extension, engine),
            LapNpNumericDataType(extension, engine),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        engine = LapNpEngine(karooSystem, scope)
        // Los consumidores se registran ya; el SDK los activa al conectar.
        engine.start()
        karooSystem.connect()
    }

    override fun onDestroy() {
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "lapnp"
    }
}
