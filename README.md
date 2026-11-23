# Lap NP: extensión para Karoo 3

Campo de datos que muestra la **potencia normalizada (NP) de la vuelta o intervalo en curso** y pinta el fondo según el objetivo del entreno de TrainingPeaks:

| Color | Significado |
|---|---|
| 🟢 Verde | La NP del intervalo está dentro del rango objetivo |
| 🔵 Azul ▲ | Por debajo: hay que apretar |
| 🔴 Rojo ▼ | Por encima: hay que aflojar |
| ⚫ Gris | Primeros 10 s del intervalo (valor aún poco fiable) |
| ⬛ Negro | No hay objetivo de potencia (sin entreno o intervalo de FC/cadencia) |

La línea inferior muestra el rango (p. ej. `250–270`) y los vatios que te sobran o te faltan (`-12`, `+8`).

Se incluyen dos campos:
- **NP vuelta vs objetivo** (gráfico, con colores).
- **NP vuelta** (numérico simple), por si lo quieres en cualquier página.

## Cómo calcula

- Una muestra de potencia por segundo, solo mientras la salida se está grabando (las pausas no cuentan).
- Algoritmo NP estándar: media móvil de 30 s → 4ª potencia → media → raíz cuarta.
- **Durante los primeros 30 s del intervalo no existe NP**, así que se muestra la potencia media (la cabecera pasa a «MEDIA VUELTA»).
- Se reinicia en cada vuelta (manual, automática o creada por el entreno) y en cada cambio de paso del entreno.
- Los ceros por dejar de pedalear cuentan; los cortes del sensor no.
- El objetivo sale del campo `WORKOUT_POWER_TARGET` del Karoo (mín./máx. del intervalo). Si el entreno solo trae un valor, se aplica ±5 % igual que el Karoo.

> En intervalos cortos (menos de ~2–3 min) la NP no es representativa. En esos casos fíjate más en la media.

## Compilar

karoo-ext se publica en GitHub Packages, que siempre pide autenticación aunque el paquete sea público.

### Opción A: GitHub Actions (sin instalar nada)

1. Crea un repositorio (privado vale) y sube esta carpeta.
2. El workflow `.github/workflows/build.yml` se ejecuta solo, pasa los tests y genera el APK.
3. Descárgalo desde la pestaña **Actions → última ejecución → Artifacts → lap-np-apk**.

### Opción B: Android Studio / línea de comandos

1. Crea un token de GitHub (classic) con el permiso `read:packages`.
2. Añade a `~/.gradle/gradle.properties`:
   ```
   gpr.user=TU_USUARIO_GITHUB
   gpr.key=ghp_xxxxxxxxxxxx
   ```
3. `./gradlew testDebugUnitTest assembleRelease`
4. El APK queda en `app/build/outputs/apk/release/`.

## Instalar en el Karoo 3

- **Sin cable:** sube el APK a una URL pública (p. ej. una *release* de GitHub), abre la app Companion de Hammerhead en el móvil, comparte el enlace con ella y confirma la instalación en el Karoo.
- **Con adb:** activa las opciones de desarrollador del Karoo y ejecuta `adb install -r app-release.apk`.

## Ponerlo en pantalla

1. En el Karoo: **Perfiles → (tu perfil) → página de entreno** o **Lap & Workout**.
2. Añade un campo → sección de extensiones → **Lap NP → NP vuelta vs objetivo**.
3. Recomendado: tamaño de medio ancho o mayor, para que se lea la línea del rango.

## Primera salida: qué comprobar

- Que el rango de la línea inferior coincide con el que muestra el campo nativo «Primary target». Si sale vacío o raro, el Karoo está entregando el objetivo en otro formato: revisa `TargetZone.build()`.
- Que al empezar cada intervalo el campo se reinicia (gris unos segundos y «MEDIA VUELTA»).

## Estructura

```
app/src/main/kotlin/es/lapnp/karoo/
  LapNpCalculator.kt   cálculo NP/media (sin Android, con tests)
  TargetZone.kt        rango objetivo y color
  LapNpEngine.kt       streams del Karoo, reloj de 1 Hz y reinicios
  LapNpDataTypes.kt    los dos campos (gráfico y numérico)
  LapNpExtension.kt    servicio de la extensión
```
