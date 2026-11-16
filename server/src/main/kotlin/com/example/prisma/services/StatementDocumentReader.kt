package com.example.prisma.services

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.min

data class StatementInputFile(
    val bytes: ByteArray,
    val mimeType: String,
)

data class StatementText(
    val pages: List<String>,
    val warnings: List<String>,
)

class StatementOcrUnavailable(
    message: String,
) : Exception(message)

object StatementDocumentReader {
    private const val MAX_PAGES = 12
    private const val MAX_PIXELS = 20_000_000L
    private const val MAX_TEXT = 200_000

    fun read(files: List<StatementInputFile>): StatementText {
        require(files.isNotEmpty() && files.size <= 12) {
            "Selecciona entre 1 y 12 archivos."
        }

        require(
            files.all {
                it.bytes.isNotEmpty() &&
                        it.bytes.size <= 10 * 1024 * 1024
            },
        ) {
            "Cada archivo debe contener datos y pesar como máximo 10 MB."
        }

        require(
            files.sumOf { it.bytes.size.toLong() } <=
                    20L * 1024 * 1024,
        ) {
            "El conjunto de archivos supera 20 MB."
        }

        val pages = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var ocrPages = 0

        val deadline = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(180)

        try {
            files.forEach { file ->
                require(pages.size < MAX_PAGES) {
                    "El estado debe tener como máximo 12 páginas."
                }

                when (file.mimeType) {
                    "application/pdf" -> {
                        Loader.loadPDF(file.bytes).use { document ->
                            require(!document.isEncrypted) {
                                "El PDF tiene contraseña o protección. " +
                                        "Exporta una copia sin contraseña."
                            }

                            require(
                                document.numberOfPages in
                                        1..(MAX_PAGES - pages.size),
                            ) {
                                "El estado debe tener entre 1 y 12 páginas en total."
                            }

                            val renderer = PDFRenderer(document)

                            val stripper = PDFTextStripper().apply {
                                sortByPosition = true
                            }

                            for (index in 0 until document.numberOfPages) {
                                remainingSeconds(deadline)

                                stripper.startPage = index + 1
                                stripper.endPage = index + 1

                                val native = stripper
                                    .getText(document)
                                    .trim()

                                require(native.length <= MAX_TEXT) {
                                    "Una página contiene demasiado texto."
                                }

                                val text = if (
                                    native.count {
                                        it.isLetterOrDigit()
                                    } >= 30
                                ) {
                                    native
                                } else {
                                    val box = document
                                        .getPage(index)
                                        .cropBox

                                    checkDimensions(
                                        box.width.toDouble() * 150 / 72,
                                        box.height.toDouble() * 150 / 72,
                                    )

                                    ocrPages++

                                    val scanned = ocr(deadline) { input ->
                                        val image = renderer
                                            .renderImageWithDPI(
                                                index,
                                                150f,
                                                ImageType.RGB,
                                            )

                                        try {
                                            check(
                                                ImageIO.write(
                                                    image,
                                                    "png",
                                                    input.toFile(),
                                                ),
                                            )
                                        } finally {
                                            image.flush()
                                        }
                                    }

                                    listOf(native, scanned)
                                        .filter { it.isNotBlank() }
                                        .joinToString("\n")
                                }

                                pages += text
                            }
                        }
                    }

                    "image/jpeg", "image/png" -> {
                        validateImage(file.bytes)
                        ocrPages++

                        pages += ocr(deadline) { input ->
                            Files.write(input, file.bytes)
                            Unit
                        }
                    }

                    else -> {
                        throw IllegalArgumentException(
                            "El lector admite PDF, JPG y PNG. " +
                                    "Convierte las otras imágenes a JPG o PNG.",
                        )
                    }
                }
            }
        } catch (_: InvalidPasswordException) {
            throw IllegalArgumentException(
                "El PDF tiene contraseña. Exporta una copia sin contraseña.",
            )
        } catch (_: IOException) {
            throw IllegalArgumentException(
                "No se pudo abrir el documento. " +
                        "Comprueba que el archivo no esté dañado.",
            )
        }

        if (ocrPages > 0) {
            warnings += "Se utilizó OCR en " + ocrPages +
                    " páginas. Revisa los importes y las fechas."
        }

        pages.forEachIndexed { index, text ->
            if (text.isBlank()) {
                warnings += "No se obtuvo texto de la página " +
                        (index + 1) + "."
            }
        }

        return StatementText(pages, warnings)
    }

    private fun validateImage(bytes: ByteArray) {
        ImageIO.createImageInputStream(
            ByteArrayInputStream(bytes),
        ).use { stream ->
            require(stream != null) {
                "No se pudo abrir la imagen."
            }

            val readers = ImageIO.getImageReaders(stream)

            require(readers.hasNext()) {
                "La imagen no es un JPG o PNG válido."
            }

            val reader = readers.next()

            try {
                reader.input = stream

                checkDimensions(
                    reader.getWidth(0).toDouble(),
                    reader.getHeight(0).toDouble(),
                )
            } finally {
                reader.dispose()
            }
        }
    }

    private fun checkDimensions(
        width: Double,
        height: Double,
    ) {
        require(
            width > 0 &&
                    height > 0 &&
                    width <= 20_000 &&
                    height <= 20_000 &&
                    width * height <= MAX_PIXELS,
        ) {
            "Una página o imagen es demasiado grande. " +
                    "Reduce su resolución y vuelve a intentarlo."
        }
    }

    private fun remainingSeconds(deadline: Long): Long {
        val seconds = TimeUnit.NANOSECONDS.toSeconds(
            deadline - System.nanoTime(),
        )

        if (seconds < 1) {
            throw StatementOcrUnavailable(
                "La lectura tardó demasiado. " +
                        "Envía menos páginas por intento.",
            )
        }

        return seconds
    }

    private fun ocr(
        deadline: Long,
        writeInput: (Path) -> Unit,
    ): String {
        val directory = try {
            Files.createTempDirectory("prisma-ocr-")
        } catch (_: IOException) {
            throw StatementOcrUnavailable(
                "No se pudo preparar el lector de imágenes.",
            )
        }

        var process: Process? = null

        try {
            val input = directory.resolve("page")
            val output = directory.resolve("text.txt")
            val errors = directory.resolve("errors.txt")

            writeInput(input)

            val binary = System.getenv("PRISMA_TESSERACT_PATH")
                ?.takeIf { it.isNotBlank() }
                ?: "tesseract"

            val languages = System.getenv("PRISMA_OCR_LANG")
                ?.takeIf { it.isNotBlank() }
                ?: "spa+eng"

            process = try {
                ProcessBuilder(
                    binary,
                    input.toString(),
                    "stdout",
                    "-l",
                    languages,
                    "--psm",
                    "6",
                )
                    .redirectOutput(output.toFile())
                    .redirectError(errors.toFile())
                    .start()
            } catch (_: IOException) {
                throw StatementOcrUnavailable(
                    "El lector OCR no está instalado o " +
                            "no se puede ejecutar en el servidor.",
                )
            }

            if (
                !process.waitFor(
                    min(30L, remainingSeconds(deadline)),
                    TimeUnit.SECONDS,
                )
            ) {
                process.destroyForcibly()
                    .waitFor(3, TimeUnit.SECONDS)

                throw StatementOcrUnavailable(
                    "La lectura de una imagen tardó demasiado. " +
                            "Usa una imagen más clara o pequeña.",
                )
            }

            val diagnostic = Files.newInputStream(errors).use {
                it.readNBytes(8_192)
                    .toString(Charsets.UTF_8)
            }

            val missingLanguage = listOf(
                "Failed loading language",
                "Error opening data file",
                "Couldn't load any languages",
            ).any {
                diagnostic.contains(
                    it,
                    ignoreCase = true,
                )
            }

            if (missingLanguage) {
                throw StatementOcrUnavailable(
                    "Faltan los idiomas del OCR. " +
                            "Instala los paquetes de español e inglés en el servidor.",
                )
            }

            require(process.exitValue() == 0) {
                "El OCR no pudo leer la imagen. " +
                        "Comprueba su formato y vuelve a intentarlo."
            }

            require(Files.size(output) <= MAX_TEXT * 4L) {
                "Una página contiene demasiado texto."
            }

            val text = Files.readString(
                output,
                Charsets.UTF_8,
            ).trim()

            require(text.length <= MAX_TEXT) {
                "Una página contiene demasiado texto."
            }

            return text
        } catch (_: IOException) {
            throw StatementOcrUnavailable(
                "No se pudo completar la lectura OCR en el servidor.",
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()

            throw StatementOcrUnavailable(
                "Se interrumpió la lectura OCR. Inténtalo de nuevo.",
            )
        } finally {
            process?.takeIf { it.isAlive }?.let { child ->
                child.destroyForcibly()

                runCatching {
                    child.waitFor(3, TimeUnit.SECONDS)
                }
            }

            runCatching {
                Files.walk(directory).use { paths ->
                    paths.sorted(
                        Comparator.reverseOrder(),
                    ).forEach { path ->
                        runCatching {
                            Files.deleteIfExists(path)
                        }
                    }
                }
            }
        }
    }
}