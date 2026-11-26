# Contribuir a VideoEcraft

¡Gracias por tu interés! Este documento explica cómo enviar cambios para que
la revisión sea rápida.

---

## Antes de empezar

- Abre un **issue** antes de trabajar en un cambio grande (una nueva
  herramienta, un refactor del motor). Evita trabajo duplicado.
- Para arreglos pequeños (typos, bugs obvios) puedes ir directo al PR.

---

## Entorno

| Herramienta | Versión |
|---|---|
| JDK | **17** |
| Android Studio | Meerkat (2025.1)+ |
| Android SDK | Compile SDK 37 |

```bash
./gradlew :app:assembleDebug      # compilar
./gradlew :app:testDebugUnitTest  # tests
```

---

## Estilo de código

Seguimos el **Kotlin Coding Conventions** oficial (el proyecto ya tiene
`kotlin.code.style=official`).

### Reglas de la casa

**Nombres en español para el dominio, inglés para lo técnico.**
`EditorViewModel` es inglés (es un patrón), pero `recortarClip` o
`ClipSpec` describen el negocio en español. Mantén la coherencia.

**Doc comments en los tipos públicos.** No hace falta documentar cada
`private fun`, pero sí las clases que alguien leería al llegar al repo.
Explica **por qué**, no **qué**: el código ya dice lo que hace.

**Sin `@Suppress` como parche.** Si un warning aparece, entiende su causa.
Si hay que silenciarlo (p. ej. `@OptIn(UnstableApi::class)` de Media3),
hazlo explícito y justificado en el comentario.

**Compose: estado arriba, eventos abajo.** Los `@Composable` reciben sus
datos por parámetro y emiten callbacks (`onXxx`). Solo `Screen` habla con
el ViewModel.

---

## Tests

Toda la lógica del motor (`engine/`) debe ser **testeable en JVM puro**.
Si tu cambio añade lógica a `VideoProcessor` o a los efectos, añade un test.

```bash
# Todos los tests
./gradlew :app:testDebugUnitTest

# Solo una clase
./gradlew :app:testDebugUnitTest --tests "*ColorAdjustmentsTest"
```

Si tu test necesita `org.json`, `Base64` u otras clases de Android,
usa `@RunWith(RobolectricTestRunner::class)` como en `EditSpecCodecTest`.

---

## Pull Requests

1. **Una rama por cambio**: `feature/chroma-presets`, `fix/timeline-zoom`.
2. **Commits atómicos** en imperativo: `Añade presets de croma`, no
   `added stuff`.
3. **Rellena la descripción**: qué problema resuelve y cómo probarlo.
4. **Verifica que compila y los tests pasan** antes de pedir revisión.
5. Si cambia la UI, **adjunta captura o GIF**.

### Checklist

- [ ] El proyecto compila (`assembleDebug`)
- [ ] Los tests pasan (`testDebugUnitTest`)
- [ ] Sin nuevos warnings en los archivos tocados
- [ ] Doc comments en los tipos públicos nuevos
- [ ] Captura/GIF si hay cambio visual

---

## Reportar bugs

Incluye:

- Modelo de dispositivo y versión de Android
- Versión de la app (aparece en **Créditos**)
- Pasos reproducibles
- `adb logcat` filtrado por tu app si hay un crash

```bash
adb logcat | grep -i videoecraft
```

---

## Licencia

Al contribuir aceptas que tu código se distribuya bajo la
**Apache License 2.0**, la misma del proyecto.
