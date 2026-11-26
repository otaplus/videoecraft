package com.example.videoecraft.engine.effects

/**
 * Shaders GLSL compartidos por los efectos de VideoEcraft.
 *
 * Todos los shaders usan la convención de atributos de Media3:
 *  - `aFramePosition`     → coordenadas normalizadas de clip (-1..1)
 *  - `aTexSamplingCoord`  → coordenadas de textura (0..1)
 *  - `uTexSampler`        → sampler `samplerExternalOES` de la entrada
 */
internal object Shaders {

    /** Vertex shader común: quad texto-pantalla con transformación identidad. */
    const val VERTEX = """
        attribute vec4 aFramePosition;
        attribute vec4 aTexSamplingCoord;
        varying vec2 vTexSamplingCoord;

        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = aTexSamplingCoord.xy;
        }
    """

    /**
     * Copia la textura de entrada a la salida sin transformación.
     * Se usa cuando un efecto está en su estado neutro para no gastar
     * un pase de color innecesario.
     */
    const val VERTEX_TO_FRAGMENT = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        varying vec2 vTexSamplingCoord;

        void main() {
            gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
        }
    """

    /**
     * Matriz de color 4x4 aplicada sobre RGB (misma idea que `RgbMatrix`).
     * `uColorMatrix` va en column-major, como `android.graphics.ColorMatrix`
     * adaptada a RGBA.
     */
    const val COLOR_MATRIX = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        uniform mat4 uColorMatrix;
        varying vec2 vTexSamplingCoord;

        void main() {
            vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
            vec3 rgb = (uColorMatrix * vec4(color.rgb, 1.0)).rgb;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    /**
     * Chroma Key con supresión de *spill* (sangrado del color clave sobre
     * el sujeto).
     *
     * La distancia se calcula en el plano de croma YUV e ignora la
     * luminancia, de modo que las sombras oscuras del sujeto no se
     * recortan. `smoothstep` produce un alfa continuo en el borde.
     *
     * @param uKeyColor       color clave normalizado 0..1 (p. ej. verde puro)
     * @param uSimilarity     umbral: por debajo el píxel es totalmente transparente
     * @param uSmoothness     ancho de la transición (bordes más suaves)
     * @param uSpillReduction cuánto se desatura el tinte residual del color clave
     */
    const val CHROMA_KEY = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        uniform vec3 uKeyColor;
        uniform float uSimilarity;
        uniform float uSmoothness;
        uniform float uSpillReduction;
        varying vec2 vTexSamplingCoord;

        const vec3 YUV_FROM_RGB_R = vec3( 0.299,    0.587,    0.114);
        const vec3 YUV_FROM_RGB_G = vec3(-0.168736,-0.331264, 0.5);
        const vec3 YUV_FROM_RGB_B = vec3( 0.5,     -0.418688, -0.081312);

        // Distancia de croma normalizada ("color difference" keying).
        float chromaDistance(vec3 pixel, vec3 key) {
            vec3 pixelYuv = vec3(
                dot(pixel, YUV_FROM_RGB_R),
                dot(pixel, YUV_FROM_RGB_G),
                dot(pixel, YUV_FROM_RGB_B)
            );
            vec3 keyYuv = vec3(
                dot(key, YUV_FROM_RGB_R),
                dot(key, YUV_FROM_RGB_G),
                dot(key, YUV_FROM_RGB_B)
            );
            // Se ignora la luminancia (.x): solo importa el croma (.yz).
            return distance(pixelYuv.yz, keyYuv.yz);
        }

        void main() {
            vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
            float dist = chromaDistance(color.rgb, uKeyColor);

            // 0 = transparente (fondo), 1 = opaco (sujeto)
            float alpha = smoothstep(
                max(uSimilarity - uSmoothness, 0.0),
                uSimilarity + uSmoothness + 0.0001,
                dist
            );

            // Spill suppression: resta la dominancia del canal clave.
            float keyDominance = max(color.g - max(color.r, color.b), 0.0)
                               + max(color.b - max(color.r, color.g), 0.0);
            vec3 despilled = color.rgb - keyDominance * uSpillReduction *
                normalize(uKeyColor + vec3(0.0001));

            gl_FragColor = vec4(clamp(despilled, 0.0, 1.0), color.a * alpha);
        }
    """

    /**
     * Desenfoque gaussiano separable (13 muestras).
     *
     * Se reutiliza el mismo shader en pasada horizontal y vertical
     * variando `uDirection`, que es el patrón estándar para blur barato
     * en GPU. Se usa para el fondo "desenfoque del propio video".
     *
     * @param uTexelSize tamaño de un texel en UV (1/ancho, 1/alto)
     * @param uDirection (1,0) horizontal · (0,1) vertical
     * @param uRadius    radio del kernel en texeles
     */
    const val BLUR = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        uniform vec2 uTexelSize;
        uniform vec2 uDirection;
        uniform float uRadius;
        varying vec2 vTexSamplingCoord;

        void main() {
            vec4 sum = vec4(0.0);
            float weightSum = 0.0;
            float sigma = max(uRadius, 0.0001);
            float step_ = clamp(uRadius / 6.0, 0.0, 1.0);

            for (int i = -6; i <= 6; i++) {
                float offset = float(i) * step_;
                float weight = exp(-(offset * offset) / (2.0 * sigma * sigma));
                vec2 coord = vTexSamplingCoord + uDirection * uTexelSize * offset;
                sum += texture2D(uTexSampler, coord) * weight;
                weightSum += weight;
            }
            gl_FragColor = sum / weightSum;
        }
    """

    /**
     * Fundido de entrada controlado por tiempo.
     *
     * @param uProgress   0..1 · progreso de la transición.
     * @param uFromBlack  1 = funde el RGB desde negro · 0 = anima el alfa
     *                    (disolución sobre la capa inferior).
     */
    const val FADE = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        uniform float uProgress;
        uniform float uFromBlack;
        varying vec2 vTexSamplingCoord;

        void main() {
            vec4 color = texture2D(uTexSampler, vTexSamplingCoord);

            // Curva suave (ease-in-out) para que el fundido no sea lineal.
            float p = clamp(uProgress, 0.0, 1.0);
            float eased = p * p * (3.0 - 2.0 * p);

            // uFromBlack = 1 → atenúa RGB (sale de negro, alfa intacto).
            // uFromBlack = 0 → atenúa alfa (disolución sobre la capa inferior).
            float rgbScale   = mix(1.0, eased, uFromBlack);
            float alphaScale = mix(eased, 1.0, uFromBlack);

            gl_FragColor = vec4(color.rgb * rgbScale, color.a * alphaScale);
        }
    """

    /** Viñeta radial: oscurece las esquinas. Usada en la transición "Zoom". */
    const val VIGNETTE = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        uniform samplerExternalOES uTexSampler;
        uniform float uStrength;
        varying vec2 vTexSamplingCoord;

        void main() {
            vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
            vec2 centered = vTexSamplingCoord - vec2(0.5);
            float dist = length(centered) * 1.414;
            float vignette = smoothstep(1.0 - uStrength, 1.0, dist);
            gl_FragColor = vec4(color.rgb * (1.0 - vignette), color.a);
        }
    """
}
