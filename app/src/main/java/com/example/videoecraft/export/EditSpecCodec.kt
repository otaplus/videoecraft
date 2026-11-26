package com.example.videoecraft.export

import android.util.Base64
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.engine.PlaybackSpeed
import com.example.videoecraft.engine.effects.ChromaKeySettings
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.TransitionType
import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.engine.model.BackgroundFill
import com.example.videoecraft.engine.model.CanvasSpec
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportResolution
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import com.example.videoecraft.engine.model.TextLayerSpec
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Serialización ligera de [EditSpec] a JSON (Base64 para los `Bundle`).
 *
 * Se usa `org.json` (incluido en el framework) en lugar de
 * `kotlinx.serialization` para no añadir un plugin de compilación solo por
 * transportar el spec entre la UI y el `ExportService` por un Intent.
 *
 * En la Fase 3, Room persistirá el proyecto y este códec se reutilizará
 * como columna `TEXT` del borrador, así que el formato es **versionado**
 * ([VERSION]) para poder migrarlo sin romper borradores guardados.
 */
@UnstableApi
object EditSpecCodec {

    /** Versión del formato. Incrementar al cambiar el esquema. */
    private const val VERSION = 1

    // ---------------------------------------------------------------
    //  Escritura
    // ---------------------------------------------------------------

    fun encode(spec: EditSpec): String {
        val root = JSONObject().apply {
            put("v", VERSION)

            put(
                "canvas",
                JSONObject().apply {
                    put("ar", spec.canvas.aspectRatio.name)
                    put("res", spec.canvas.resolution.name)
                    put(
                        "bg",
                        // No se encadena `.put()`: devuelve `null` en el stub de
                        // Android para unit tests. Se usa `apply` explícito.
                        when (val fill = spec.canvas.background) {
                            is BackgroundFill.Blur -> JSONObject().apply {
                                put("type", "blur")
                                put("radius", fill.radius.toDouble())
                            }

                            is BackgroundFill.Solid -> JSONObject().apply {
                                put("type", "solid")
                                put("color", fill.colorArgb)
                            }

                            BackgroundFill.Black -> JSONObject().apply {
                                put("type", "black")
                            }
                        },
                    )
                },
            )

            put("clips", JSONArray().also { array -> spec.clips.forEach { array.put(encodeClip(it)) } })
            put("overlays", JSONArray().also { array -> spec.overlays.forEach { array.put(encodeOverlay(it)) } })
            put("music", JSONArray().also { array -> spec.music.forEach { array.put(encodeMusic(it)) } })
            put("texts", JSONArray().also { array -> spec.texts.forEach { array.put(encodeText(it)) } })

            put("musicVolume", spec.musicVolume.toDouble())
            put("originalVolume", spec.originalVolume.toDouble())
        }

        return Base64.encodeToString(
            root.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    }

    private fun encodeClip(clip: ClipSpec): JSONObject = JSONObject().apply {
        put("uri", clip.uri)
        put("start", clip.sourceStartMs)
        put("end", clip.sourceEndMs)
        put("speed", clip.speed.factor.toDouble())
        put("color", encodeColor(clip.color))
        put("tIn", clip.transitionIn.name)
        put("tDur", clip.transitionDurationMs)
        put("audio", clip.keepOriginalAudio)
        put("srcDur", clip.sourceDurationMs)
    }

    private fun encodeOverlay(overlay: OverlaySpec): JSONObject = JSONObject().apply {
        put("uri", overlay.uri)
        put("start", overlay.startMs)
        put("end", overlay.endMs)
        overlay.chromaKey?.let { key ->
            put(
                "chroma",
                JSONObject().apply {
                    put("r", key.keyColor.first.toDouble())
                    put("g", key.keyColor.second.toDouble())
                    put("b", key.keyColor.third.toDouble())
                    put("sim", key.similarity.toDouble())
                    put("smooth", key.smoothness.toDouble())
                    put("spill", key.spillReduction.toDouble())
                },
            )
        }
        put("color", encodeColor(overlay.color))
        put("scale", overlay.scale.toDouble())
        put("x", overlay.positionX.toDouble())
        put("y", overlay.positionY.toDouble())
        put("rot", overlay.rotationDegrees.toDouble())
        put("alpha", overlay.alpha.toDouble())
    }

    private fun encodeMusic(music: MusicSpec): JSONObject = JSONObject().apply {
        put("uri", music.uri)
        put("start", music.startMs)
        put("end", music.endMs)
        put("loop", music.loop)
        put("volume", music.volume.toDouble())
        put("trimStart", music.trimStartMs)
        put("trimEnd", music.trimEndMs)
    }

    private fun encodeText(text: TextLayerSpec): JSONObject = JSONObject().apply {
        put("text", text.text)
        put("start", text.startMs)
        put("end", text.endMs)
        put("x", text.x.toDouble())
        put("y", text.y.toDouble())
        put("rot", text.rotation.toDouble())
        put("size", text.fontSizeSp.toDouble())
        put("color", text.colorArgb)
        put("bg", text.backgroundColorArgb)
        put("bold", text.bold)
    }

    private fun encodeColor(adjustments: ColorAdjustments): JSONObject = JSONObject().apply {
        put("b", adjustments.brightness.toDouble())
        put("c", adjustments.contrast.toDouble())
        put("s", adjustments.saturation.toDouble())
        put("w", adjustments.warmth.toDouble())
        put("t", adjustments.tint.toDouble())
    }

    // ---------------------------------------------------------------
    //  Lectura
    // ---------------------------------------------------------------

    /** Devuelve `null` si el JSON está corrupto o es de otra versión. */
    fun decode(encoded: String): EditSpec? = runCatching {
        val json = String(
            Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE),
            StandardCharsets.UTF_8,
        )
        val root = JSONObject(json)

        // Migraciones futuras se encadenan aquí según root.optInt("v").
        if (root.optInt("v", 0) != VERSION) return null

        EditSpec(
            canvas = decodeCanvas(root.getJSONObject("canvas")),
            clips = root.optJSONArray("clips").mapObjects { decodeClip(it) },
            overlays = root.optJSONArray("overlays").mapObjects { decodeOverlay(it) },
            music = root.optJSONArray("music").mapObjects { decodeMusic(it) },
            texts = root.optJSONArray("texts").mapObjects { decodeText(it) },
            musicVolume = root.optDouble("musicVolume", 0.6).toFloat(),
            originalVolume = root.optDouble("originalVolume", 1.0).toFloat(),
        )
    }.getOrNull()

    private fun decodeCanvas(json: JSONObject): CanvasSpec = CanvasSpec(
        aspectRatio = enumOr(json.optString("ar"), AspectRatio.ORIGINAL),
        resolution = enumOr(json.optString("res"), ExportResolution.P1080),
        background = json.optJSONObject("bg")?.let { bg ->
            when (bg.optString("type")) {
                "solid" -> BackgroundFill.Solid(bg.optInt("color", 0xFF000000.toInt()))
                "black" -> BackgroundFill.Black
                else -> BackgroundFill.Blur(bg.optDouble("radius", 20.0).toFloat())
            }
        } ?: BackgroundFill.Blur(),
    )

    private fun decodeClip(json: JSONObject): ClipSpec = ClipSpec(
        uri = json.getString("uri"),
        sourceStartMs = json.optLong("start", 0L),
        sourceEndMs = json.optLong("end", -1L),
        speed = PlaybackSpeed(
            json.optDouble("speed", 1.0).toFloat()
                .coerceIn(PlaybackSpeed.MIN, PlaybackSpeed.MAX),
        ),
        color = decodeColor(json.optJSONObject("color")),
        transitionIn = enumOr(json.optString("tIn"), TransitionType.NONE),
        transitionDurationMs = json.optLong("tDur", 500L),
        keepOriginalAudio = json.optBoolean("audio", true),
        sourceDurationMs = json.optLong("srcDur", 0L),
    )

    private fun decodeOverlay(json: JSONObject): OverlaySpec = OverlaySpec(
        uri = json.getString("uri"),
        startMs = json.optLong("start", 0L),
        endMs = json.optLong("end", -1L),
        chromaKey = json.optJSONObject("chroma")?.let { key ->
            ChromaKeySettings(
                keyColor = Triple(
                    key.optDouble("r", 0.0).toFloat(),
                    key.optDouble("g", 1.0).toFloat(),
                    key.optDouble("b", 0.0).toFloat(),
                ),
                similarity = key.optDouble("sim", 0.35).toFloat(),
                smoothness = key.optDouble("smooth", 0.08).toFloat(),
                spillReduction = key.optDouble("spill", 0.6).toFloat(),
            )
        },
        color = decodeColor(json.optJSONObject("color")),
        scale = json.optDouble("scale", 0.4).toFloat(),
        positionX = json.optDouble("x", 0.6).toFloat(),
        positionY = json.optDouble("y", 0.6).toFloat(),
        rotationDegrees = json.optDouble("rot", 0.0).toFloat(),
        alpha = json.optDouble("alpha", 1.0).toFloat(),
    )

    private fun decodeMusic(json: JSONObject): MusicSpec = MusicSpec(
        uri = json.getString("uri"),
        startMs = json.optLong("start", 0L),
        endMs = json.optLong("end", -1L),
        loop = json.optBoolean("loop", true),
        volume = json.optDouble("volume", 0.6).toFloat(),
        trimStartMs = json.optLong("trimStart", 0L),
        trimEndMs = json.optLong("trimEnd", -1L),
    )

    private fun decodeText(json: JSONObject): TextLayerSpec = TextLayerSpec(
        text = json.getString("text"),
        startMs = json.optLong("start", 0L),
        endMs = json.optLong("end", -1L),
        x = json.optDouble("x", 0.5).toFloat(),
        y = json.optDouble("y", 0.5).toFloat(),
        rotation = json.optDouble("rot", 0.0).toFloat(),
        fontSizeSp = json.optDouble("size", 24.0).toFloat(),
        colorArgb = json.optInt("color", 0xFFFFFFFF.toInt()),
        backgroundColorArgb = json.optInt("bg", 0),
        bold = json.optBoolean("bold", true),
    )

    private fun decodeColor(json: JSONObject?): ColorAdjustments {
        if (json == null) return ColorAdjustments.NEUTRAL
        return ColorAdjustments(
            brightness = json.optDouble("b", 0.0).toFloat(),
            contrast = json.optDouble("c", 1.0).toFloat(),
            saturation = json.optDouble("s", 1.0).toFloat(),
            warmth = json.optDouble("w", 0.0).toFloat(),
            tint = json.optDouble("t", 0.0).toFloat(),
        )
    }

    /** `enumOr` tolerante: si el nombre no existe, devuelve el valor por defecto. */
    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    /** Mapea un `JSONArray` nullable a una lista, saltando elementos inválidos. */
    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { runCatching { transform(it) }.getOrNull() }
        }
    }
}
