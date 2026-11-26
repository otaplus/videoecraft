package com.example.videoecraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.videoecraft.data.db.VideoEcraftDatabase
import com.example.videoecraft.data.repository.ProjectRepository
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests del ciclo de vida de los medios importados.
 *
 * Cubre la **fuga de disco** detectada en revisión: los MP4 se copiaban al
 * sandbox pero nada los borraba al eliminar el proyecto.
 *
 * Se ejecuta con Robolectric porque necesita un [Context] real y Room.
 *
 * La parte más crítica son las **guardas de seguridad**: es preferible no
 * borrar a borrar un archivo del usuario por error.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaFileStoreTest {

    private lateinit var context: Context
    private lateinit var repository: ProjectRepository
    private lateinit var database: VideoEcraftDatabase

    /**
     * Carpeta temporal propia del test.
     *
     * No se usa `filesDir` de Robolectric porque entre llamadas puede
     * devolver rutas distintas. Un directorio real y estable por test hace
     * las aserciones deterministas.
     */
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VideoEcraftDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ProjectRepository(
            projectDao = database.projectDao(),
            // `runBlocking` espera de verdad a `Dispatchers.IO`, así que la
            // carrera entre corrutinas y `tearDown` desaparece. Aun así se
            // inyecta el dispatcher para dejar explícito el contrato.
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
        )

        // Se usa un directorio dentro de `build/` en lugar de `createTempFile`:
        // en Windows, la carpeta temporal del sistema la pueden limpiar
        // antivirus o el propio SO entre llamadas, lo que vaciaba el
        // directorio a mitad de test y producía fallos fantasmas.
        val baseDir = File("build/test-media-store").apply { deleteRecursively(); mkdirs() }
        tempDir = File(baseDir, "case-${System.nanoTime()}").apply { mkdirs() }
        MediaFileStore.overrideDirectoryForTest(tempDir)
    }

    @After
    fun tearDown() {
        MediaFileStore.overrideDirectoryForTest(null)
        tempDir.deleteRecursively()
        database.close()
    }

    /** Crea un archivo falso en el sandbox con el nombre esperado. */
    private fun fakeMediaFile(name: String): File =
        File(MediaFileStore.directory(context), name).apply {
            writeText("datos de prueba")
        }

    // =================================================================
    //  Borrado al eliminar el proyecto
    // =================================================================

    @Test
    fun `eliminar el proyecto borra sus archivos del sandbox`() = kotlinx.coroutines.runBlocking {
        val clip = fakeMediaFile("proj1_aaa.mp4")
        val music = fakeMediaFile("proj1_bbb.m4a")
        val unrelated = fakeMediaFile("proj2_ccc.mp4")

        // Precondición sobre los `File` recién creados, no sobre un re-listado.
        assertTrue("precondición: el clip existe", clip.exists())
        assertTrue("precondición: la música existe", music.exists())
        assertTrue("precondición: el ajeno existe", unrelated.exists())

        val spec = EditSpec(
            clips = listOf(ClipSpec(uri = "file://${clip.absolutePath}")),
            music = listOf(MusicSpec(uri = "file://${music.absolutePath}")),
        )

        // Traza inmediatamente antes de crear el proyecto.
        val beforeCreate = MediaFileStore.directory(context).listFiles()?.map { it.name }?.sorted()
        assertEquals("antes de createProject: $beforeCreate", 3, beforeCreate?.size)

        val projectId = repository.createProject("Test", spec)

        val afterCreate = MediaFileStore.directory(context).listFiles()?.map { it.name }?.sorted()
        assertEquals(
            "tras createProject deben seguir los 3 archivos: $afterCreate | " +
                "tempDirExiste=${tempDir.exists()} | " +
                "tempDir=${tempDir.absolutePath} | " +
                "dirActual=${MediaFileStore.directory(context).absolutePath} | " +
                "tempDirList=${tempDir.listFiles()?.map { it.name }}",
            3,
            afterCreate?.size,
        )

        val deleted = repository.deleteProjectWithMedia(context, projectId)

        assertEquals("deben borrarse 2 medios del proyecto", 2, deleted)
        assertFalse("el clip debe borrarse", clip.exists())
        assertFalse("la música debe borrarse", music.exists())

        // Diagnóstico del ajeno: ¿qué ruta tenía y a qué padre resuelve?
        assertTrue(
            "un archivo que no referencia el proyecto no se toca. " +
                "existe=${unrelated.exists()}, " +
                "quedan=${MediaFileStore.directory(context).listFiles()?.map { it.name }}",
            unrelated.exists(),
        )
    }

    @Test
    fun `eliminar el proyecto tambien borra los overlays`() = kotlinx.coroutines.runBlocking {
        val clip = fakeMediaFile("p3_clip.mp4")
        val overlay = fakeMediaFile("p3_overlay.mp4")

        val spec = EditSpec(
            clips = listOf(ClipSpec(uri = "file://${clip.absolutePath}")),
            overlays = listOf(OverlaySpec(uri = "file://${overlay.absolutePath}")),
        )
        val projectId = repository.createProject("Test", spec)

        repository.deleteProjectWithMedia(context, projectId)

        assertFalse(clip.exists())
        assertFalse("el overlay también es un medio del proyecto", overlay.exists())
    }

    @Test
    fun `la fila de Room se elimina junto con los archivos`() = kotlinx.coroutines.runBlocking {
        val clip = fakeMediaFile("p4_aaa.mp4")
        val spec = EditSpec(clips = listOf(ClipSpec(uri = "file://${clip.absolutePath}")))
        val projectId = repository.createProject("Test", spec)

        repository.deleteProjectWithMedia(context, projectId)

        assertEquals(null, repository.getProject(projectId))
    }

    // =================================================================
    //  Guardas de seguridad — NUNCA borrar archivos ajenos
    // =================================================================

    @Test
    fun `no borra un content uri`() = kotlinx.coroutines.runBlocking {
        val deleted = MediaFileStore.deleteIfSandboxed(
            context,
            "content://media/external/video/media/123",
        )
        assertFalse(deleted)
    }

    @Test
    fun `no borra un file uri fuera del sandbox`() = kotlinx.coroutines.runBlocking {
        val outside = File(context.cacheDir, "ajeno.mp4").apply { writeText("x") }
        try {
            val deleted = MediaFileStore.deleteIfSandboxed(
                context,
                "file://${outside.absolutePath}",
            )
            assertFalse(deleted)
            assertTrue("un archivo fuera de editor/ no se toca", outside.exists())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `no borra un archivo que escapa del sandbox con punto punto`() = kotlinx.coroutines.runBlocking {
        // Intento de path traversal: el archivo real está fuera.
        val outside = File(context.filesDir, "secreto.txt").apply { writeText("x") }
        try {
            val sneaky =
                "file://${MediaFileStore.directory(context).absolutePath}/../secreto.txt"
            val deleted = MediaFileStore.deleteIfSandboxed(context, sneaky)
            assertFalse("no debe seguir rutas con ..", deleted)
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    // =================================================================
    //  Purga de huérfanos
    // =================================================================

    @Test
    fun `la purga borra archivos no referenciados`() = kotlinx.coroutines.runBlocking {
        val referenced = fakeMediaFile("p5_keep.mp4")
        val orphan = fakeMediaFile("p5_orphan.mp4")

        val spec = EditSpec(clips = listOf(ClipSpec(uri = "file://${referenced.absolutePath}")))
        repository.createProject("Test", spec)

        val deleted = repository.purgeOrphanMedia(context)

        assertEquals(1, deleted)
        assertTrue("el referenciado se conserva", referenced.exists())
        assertFalse("el huérfano se borra", orphan.exists())
    }

    @Test
    fun `la purga no borra nada si no hay proyectos`() = kotlinx.coroutines.runBlocking {
        // Guarda de seguridad: con la base de datos vacía, la purga debe
        // abstenerse. Un fallo de arranque no puede vaciar el sandbox del
        // usuario entero por un estado transitorio.
        val file = fakeMediaFile("p6_solo.mp4")

        val deleted = repository.purgeOrphanMedia(context)

        assertEquals("con cero proyectos no debe borrar nada", 0, deleted)
        assertTrue("el archivo se conserva", file.exists())
    }

    @Test
    fun `la purga conserva archivos compartidos entre proyectos`() = kotlinx.coroutines.runBlocking {
        val shared = fakeMediaFile("p7_shared.mp4")
        val spec = EditSpec(clips = listOf(ClipSpec(uri = "file://${shared.absolutePath}")))

        repository.createProject("A", spec)
        repository.createProject("B", spec)

        val deleted = repository.purgeOrphanMedia(context)

        assertEquals(0, deleted)
        assertTrue("un archivo usado por dos proyectos no se borra", shared.exists())
    }

    // =================================================================
    //  Uso de disco
    // =================================================================

    @Test
    fun `totalBytes suma el tamano de todos los medios`() = kotlinx.coroutines.runBlocking {
        fakeMediaFile("p8_a.mp4").writeText("12345")
        fakeMediaFile("p8_b.mp4").writeText("123")

        val total = MediaFileStore.totalBytes(context)

        assertEquals(8L, total)
    }
}
