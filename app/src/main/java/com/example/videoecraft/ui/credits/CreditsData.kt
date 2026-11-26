package com.example.videoecraft.ui.credits

import com.example.videoecraft.BuildConfig

/**
 * Datos de marca y créditos de VideoEcraft.
 *
 * Se centralizan aquí (y no en `strings.xml`) porque son datos de producto
 * que cambian con más frecuencia que el texto de la UI y así quedan en un
 * único punto visible al revisar el repositorio.
 *
 * > [!IMPORTANT]
 * > Antes de publicar, sustituye [OTAPLUS_URL], [OTAPLUS_CONTACT_EMAIL] y
 * > [REPOSITORY_URL] por los reales del estudio.
 */
object CreditsData {

    /** Nombre de la app. */
    const val APP_NAME = "VideoEcraft"

    /**
     * Versión mostrada en la pantalla de créditos.
     *
     * Se lee de `BuildConfig` en lugar de estar escrita a mano: así la
     * pantalla de créditos **nunca** miente sobre la build instalada, que
     * es justo el dato que el usuario necesita al reportar un bug.
     * Incluye el sufijo (`-debug`) cuando corresponde.
     */
    val APP_VERSION: String = BuildConfig.VERSION_NAME

    /** Código de versión (`versionCode`) de la build instalada. */
    val APP_VERSION_CODE: Int = BuildConfig.VERSION_CODE

    // ---------------------------------------------------------------
    //  Autor / estudio
    // ---------------------------------------------------------------

    /** Estudio responsable del proyecto. */
    const val AUTHOR_NAME = "Otaplus"

    /** Rol del estudio respecto a la app. */
    const val AUTHOR_ROLE = "Diseño y desarrollo"

    /** Lema del estudio. */
    const val AUTHOR_TAGLINE = "Software que se siente artesanal"

    /** Web del estudio. */
    const val OTAPLUS_URL = "https://otaplus.dev"

    /** Correo de contacto del estudio. */
    const val OTAPLUS_CONTACT_EMAIL = "hola@otaplus.dev"

    /** Repositorio público del proyecto. */
    const val REPOSITORY_URL = "https://github.com/otaplus/videoecraft"

    /**
     * `true` cuando las URLs siguen siendo los valores de ejemplo.
     *
     * La pantalla de créditos lo usa para avisar en builds de debug, y el
     * test `CreditsDataTest` falla si llega a release con placeholders
     * (ver el propio test para el detalle).
     */
    val usesPlaceholderLinks: Boolean
        get() = OTAPLUS_URL.contains("otaplus.dev") ||
            REPOSITORY_URL.contains("github.com/otaplus")

    // ---------------------------------------------------------------
    //  Licencia
    // ---------------------------------------------------------------

    const val LICENSE_NAME = "Apache License 2.0"
    const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

    // ---------------------------------------------------------------
    //  Tecnologías (para la sección "Hecho con")
    // ---------------------------------------------------------------

    /**
     * Stack usado, mostrado como lista en la pantalla de créditos.
     * @param name nombre legible.
     * @param description qué aporta a VideoEcraft.
     * @param license licencia del componente.
     */
    data class Technology(
        val name: String,
        val description: String,
        val license: String,
    )

    val TECHNOLOGIES: List<Technology> = listOf(
        Technology(
            name = "Jetpack Compose",
            description = "Interfaz declarativa de toda la app",
            license = "Apache 2.0",
        ),
        Technology(
            name = "AndroidX Media3",
            description = "ExoPlayer (previsualización) y Transformer (exportación)",
            license = "Apache 2.0",
        ),
        Technology(
            name = "Media3 Effect",
            description = "Shaders GLSL: chroma key, filtros y transiciones",
            license = "Apache 2.0",
        ),
        Technology(
            name = "Room",
            description = "Persistencia local de proyectos y borradores",
            license = "Apache 2.0",
        ),
        Technology(
            name = "Coil",
            description = "Miniaturas de video en la timeline",
            license = "Apache 2.0",
        ),
        Technology(
            name = "Navigation Compose",
            description = "Grafo Home → Editor → Exportación",
            license = "Apache 2.0",
        ),
        Technology(
            name = "Kotlin Coroutines",
            description = "Concurrencia estructurada y Flow",
            license = "Apache 2.0",
        ),
    )

    // ---------------------------------------------------------------
    //  Agradecimientos
    // ---------------------------------------------------------------

    /**
     * Mención a proyectos y comunidades que inspiraron el editor.
     * @param title nombre del proyecto o comunidad.
     * @param reason por qué aparece en los créditos.
     */
    data class Acknowledgement(val title: String, val reason: String)

    val ACKNOWLEDGEMENTS: List<Acknowledgement> = listOf(
        Acknowledgement(
            title = "YouCut",
            reason = "Inspiración para el modelo de herramientas y la timeline",
        ),
        Acknowledgement(
            title = "Equipo AndroidX Media",
            reason = "Por hacer viable la composición de video en GPU con Media3",
        ),
        Acknowledgement(
            title = "Comunidad open source",
            reason = "Por las librerías que hacen posible este proyecto",
        ),
    )
}
