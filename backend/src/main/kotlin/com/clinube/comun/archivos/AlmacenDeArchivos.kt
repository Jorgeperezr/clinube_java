package com.clinube.comun.archivos

import com.clinube.configuracion.Ajustes
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/**
 * Los archivos subidos, en disco, con los nombres que les pondría el
 * `FileSystemStorage` de Django: así la columna `comprobante` de un egreso
 * guarda lo mismo con los dos servidores y uno encuentra lo que subió el
 * otro (si comparten carpeta).
 *
 * En producción Django los guarda en Google Cloud Storage; aquí, en la
 * carpeta `MEDIA_ROOT` (ver docs/DIFERENCIAS.md).
 */
@Component
class AlmacenDeArchivos(ajustes: Ajustes) {

    private val raiz: Path = Path.of(ajustes.media).toAbsolutePath().normalize()
    private val azar = SecureRandom()

    companion object {
        private const val LETRAS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        /** `[^-\w.]` de Python, con `\w` de cualquier alfabeto. */
        private val NO_VALIDO = java.util.regex.Pattern.compile("""[^-\w.]""", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS)

        /**
         * `MultiPartParser.sanitize_file_name`: lo que va detrás de la última
         * barra (de cualquiera de las dos) y sin caracteres no imprimibles. Nulo
         * si no queda nombre: Django no da por recibido ese archivo.
         */
        fun nombreSubido(original: String?): String? {
            val base = (original ?: return null).substringAfterLast('/').substringAfterLast('\\')
            val limpio = buildString { base.codePoints().filter(::imprimible).forEach { appendCodePoint(it) } }
            return limpio.takeUnless { it in setOf("", ".", "..") }
        }

        /** `str.isprintable()`: ni de control, ni separadores salvo el espacio, ni sin asignar. */
        private fun imprimible(c: Int): Boolean = c == ' '.code || when (Character.getType(c).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR -> false
            else -> true
        }

        /** `get_valid_filename`: espacios a «_» y fuera lo que no sea letra, cifra, «-», «_» o «.». */
        fun nombreValido(nombre: String): String {
            val s = NO_VALIDO.matcher(nombre.trim().replace(" ", "_")).replaceAll("")
            // Django lanza `SuspiciousFileOperation`, que acaba en 500.
            check(s !in setOf("", ".", "..")) { "Nombre de archivo no válido: $nombre" }
            return s
        }

        /** `os.path.splitext`: la extensión es lo que va desde el último punto, salvo si el nombre empieza por él. */
        fun separarExtension(nombre: String): Pair<String, String> {
            val punto = nombre.lastIndexOf('.')
            if (punto <= 0 || nombre.substring(0, punto).all { it == '.' }) return nombre to ""
            return nombre.substring(0, punto) to nombre.substring(punto)
        }
    }

    /**
     * `FieldFile.save`: guarda el contenido en `carpeta/nombre` y devuelve el
     * nombre con el que quedó, que es lo que va en la columna. Si ya hay uno
     * igual, o el nombre no cabe en `maximo`, se le añade «_» y siete letras
     * al azar y se recorta la raíz lo que haga falta (`get_available_name`).
     */
    fun guardar(carpeta: String, nombre: String, contenido: InputStream, maximo: Int = 100): String {
        val (raizDelNombre, extension) = separarExtension(nombreValido(nombre))
        var raizActual = raizDelNombre
        var relativo = "$carpeta$raizActual$extension"
        while (Files.exists(raiz.resolve(relativo)) || relativo.length > maximo) {
            relativo = "$carpeta${raizActual}_${azar()}$extension"
            val sobra = relativo.length - maximo
            if (sobra > 0) {
                raizActual = raizActual.dropLast(sobra)
                check(raizActual.isNotEmpty()) { "El nombre no cabe en $maximo caracteres: $nombre" }
                relativo = "$carpeta${raizActual}_${azar()}$extension"
            }
        }
        val destino = raiz.resolve(relativo).normalize()
        check(destino.startsWith(raiz)) { "Ruta fuera de MEDIA_ROOT: $relativo" }
        Files.createDirectories(destino.parent)
        Files.copy(contenido, destino, StandardCopyOption.REPLACE_EXISTING)
        return relativo
    }

    /** El archivo de una columna, o nulo si no está en disco. */
    fun abrir(relativo: String): Path? = raiz.resolve(relativo).normalize().takeIf { it.startsWith(raiz) && Files.isRegularFile(it) }

    private fun azar(): String = buildString { repeat(7) { append(LETRAS[azar.nextInt(LETRAS.length)]) } }
}
