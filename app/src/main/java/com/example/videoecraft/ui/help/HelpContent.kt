package com.example.videoecraft.ui.help

/**
 * Contenido de la pantalla de Ayuda.
 *
 * Se modela como datos puros (sin Compose) para poder testearlo y para
 * reutilizarlo el día que se añada una sección de "preguntas frecuentes"
 * o una búsqueda interna.
 */
object HelpContent {

    /** Una pregunta frecuente con su respuesta. */
    data class FaqItem(val question: String, val answer: String)

    /** Una entrada de la guía rápida (paso a paso inicial). */
    data class QuickStartStep(val order: Int, val title: String, val body: String)

    /** Bloque de la guía de herramientas del editor. */
    data class ToolHelp(
        val toolName: String,
        val description: String,
        val tips: List<String>,
    )

    // ---------------------------------------------------------------
    //  Primeros pasos
    // ---------------------------------------------------------------

    val QUICK_START: List<QuickStartStep> = listOf(
        QuickStartStep(
            order = 1,
            title = "Importa tus videos",
            body = "Toca «Nuevo proyecto» en la pantalla de inicio y elige uno o " +
                "varios videos. No necesitas dar permisos: el selector del sistema " +
                "solo comparte los archivos que seleccionas.",
        ),
        QuickStartStep(
            order = 2,
            title = "Ordena la línea de tiempo",
            body = "Arrastra la timeline para mover el cabezal. Los clips se " +
                "seleccionan tocándolos. Usa el pellizco de dos dedos para " +
                "acercar o alejar el zoom.",
        ),
        QuickStartStep(
            order = 3,
            title = "Aplica tus herramientas",
            body = "En la barra inferior están Recortar, Transiciones, Velocidad, " +
                "Croma, Audio, Texto, Filtros y Fondo. Toca una para abrir su panel " +
                "y vuelve a tocarla para cerrarlo.",
        ),
        QuickStartStep(
            order = 4,
            title = "Exporta y comparte",
            body = "Pulsa «Exportar», elige resolución, FPS y calidad. El video se " +
                "guarda en Movies/VideoEcraft y aparece en tu galería al terminar.",
        ),
    )

    // ---------------------------------------------------------------
    //  Guía de herramientas
    // ---------------------------------------------------------------

    val TOOL_HELP: List<ToolHelp> = listOf(
        ToolHelp(
            toolName = "Recortar y dividir",
            description = "Corta los bordes de un clip o pártelo en dos en el " +
                "punto exacto del cabezal.",
            tips = listOf(
                "«Dividir» corta el clip seleccionado justo donde está el cabezal.",
                "Si el cabezal está en un extremo, la app te avisa: muévelo al centro.",
                "«Borrar» elimina el clip; siempre queda al menos uno en el proyecto.",
            ),
        ),
        ToolHelp(
            toolName = "Transiciones",
            description = "Efectos de entrada entre clips: fundido, disolución, " +
                "deslizamiento y zoom.",
            tips = listOf(
                "La transición se aplica al clip que entra, no al que sale.",
                "«Ninguna» deja un corte directo, que suele ser lo más rápido de exportar.",
                "La duración de cada transición viene prefijada por su tipo.",
            ),
        ),
        ToolHelp(
            toolName = "Velocidad",
            description = "Acelera o ralentiza un clip entre 0.2x y 4x sin " +
                "cambiar el tono de la voz.",
            tips = listOf(
                "El audio se estira con corrección de tono (time-stretching).",
                "Cambiar de velocidad cambia la duración del clip en la timeline.",
                "0.5x es el valor típico para cámara lenta suave.",
            ),
        ),
        ToolHelp(
            toolName = "Croma (pantalla verde)",
            description = "Elimina un fondo verde o azul de una capa superpuesta " +
                "sobre el video principal.",
            tips = listOf(
                "«Similitud» baja recorta solo el color exacto; alta recorta también sombras.",
                "«Suavizado» controla lo difuminado del borde; valores altos ayudan con pelo.",
                "«Spill» reduce el halo verde que el fondo deja sobre el sujeto.",
            ),
        ),
        ToolHelp(
            toolName = "Audio y música",
            description = "Añade una pista de música local y mezcla su volumen " +
                "con el audio original del video.",
            tips = listOf(
                "El interruptor «Silenciar original» quita la voz del video.",
                "La música se repite en bucle hasta cubrir toda la duración si activas el bucle.",
                "Sube la música al 100% solo si has silenciado el audio original.",
            ),
        ),
        ToolHelp(
            toolName = "Texto",
            description = "Añade capas de texto sobre el video, en la posición " +
                "del cabezal.",
            tips = listOf(
                "La capa empieza en el instante del cabezal al pulsar «Añadir».",
                "Se puede mover, rotar y redimensionar directamente sobre el visor.",
                "Usa colores claros sobre fondo oscuro para máxima legibilidad.",
            ),
        ),
        ToolHelp(
            toolName = "Filtros y ajustes",
            description = "Filtros prediseñados más control manual de brillo, " +
                "contraste, saturación y calidez.",
            tips = listOf(
                "Los filtros son combinaciones de ajustes; tocarlos sobrescribe los tuyos.",
                "Todos los ajustes se combinan en un único paso de GPU al exportar.",
                "«Restablecer» vuelve a los valores neutros del clip.",
            ),
        ),
        ToolHelp(
            toolName = "Fondo (lienzo)",
            description = "Cambia la relación de aspecto (16:9, 9:16, 1:1) y el " +
                "relleno de los bordes.",
            tips = listOf(
                "El desenfoque del propio video es el relleno más elegante.",
                "9:16 es el formato de TikTok, Reels y Shorts.",
                "Si el video no cubre el lienzo, el fondo evita franjas negras.",
            ),
        ),
    )

    // ---------------------------------------------------------------
    //  Preguntas frecuentes
    // ---------------------------------------------------------------

    val FAQ: List<FaqItem> = listOf(
        FaqItem(
            question = "¿La app pone marca de agua?",
            answer = "No. VideoEcraft nunca añade marca de agua ni límite de " +
                "duración. Lo que exportas es exactamente lo que has editado.",
        ),
        FaqItem(
            question = "¿Mis videos se suben a internet?",
            answer = "No. Todo el procesamiento ocurre en tu dispositivo con la " +
                "GPU y el codificador locales. La app no tiene permiso de red " +
                "para tus archivos y no envía nada a ningún servidor.",
        ),
        FaqItem(
            question = "¿Por qué no me pide permisos de cámara o galería?",
            answer = "Porque usa el selector de fotos del sistema (Photo Picker). " +
                "Le das acceso solo a los archivos que eliges, uno a uno. Es más " +
                "seguro para ti y cumple las políticas de Google Play.",
        ),
        FaqItem(
            question = "¿Dónde se guardan los videos exportados?",
            answer = "En la carpeta pública Movies/VideoEcraft de tu teléfono. " +
                "Aparecen en la galería en cuanto la exportación termina. Si " +
                "borras la app, esos videos se conservan.",
        ),
        FaqItem(
            question = "¿Pierdo mi trabajo si cierro la app?",
            answer = "No. Cada cambio se guarda automáticamente como borrador en " +
                "el Almacenamiento local del dispositivo. Al abrir la app verás " +
                "tus proyectos en la pantalla de inicio, listos para continuar.",
        ),
        FaqItem(
            question = "¿Qué resolución elijo para exportar?",
            answer = "720p para compartir rápido en mensajería, 1080p para redes " +
                "sociales (el mejor equilibrio calidad/tamaño) y 4K solo si tu " +
                "material original es 4K y lo necesitas para cine o archivo. A " +
                "más resolución, más tarda y más ocupa.",
        ),
        FaqItem(
            question = "¿Por qué exportar tarda tanto?",
            answer = "Exportar decodifica, aplica los efectos en la GPU y vuelve " +
                "a codificar cada fotograma. Un video de un minuto en 1080p/30fps " +
                "suele tardar entre 20 y 60 segundos según el teléfono. Los " +
                "efectos de croma añaden coste porque son un paso extra de shader.",
        ),
        FaqItem(
            question = "¿Puedo exportar mientras la pantalla está apagada?",
            answer = "Sí. La exportación corre en un servicio en primer plano, " +
                "así que el sistema no la interrumpe si sales de la app o " +
                "bloqueas el teléfono.",
        ),
    )
}
