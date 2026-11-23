package es.lapnp.karoo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Pantalla mínima: el trabajo lo hace la extensión durante la salida. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                setPadding(32, 32, 32, 32)
                textSize = 16f
                text = getString(R.string.main_help)
            },
        )
    }
}
