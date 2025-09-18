package com.amarildo.epubconverter.service

import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.SpineReference
import nl.siegmann.epublib.epub.EpubReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class TxtzConverter(
    private val epubPath: Path,
    private val txtzOutputPath: Path,
) {
    private val markdownWriter = MarkdownWriter()

    fun convertEpubToTxtz() {
        require(Files.isRegularFile(epubPath)) { "EPUB non trovato: $epubPath" }

        val parent: Path = txtzOutputPath.parent ?: Paths.get(".")
        Files.createDirectories(parent)

        val tmpDir: Path = Files.createTempDirectory("txtz_out_${Instant.now().toEpochMilli()}_")
        try {
            val book: Book = readEpub(epubPath)

            var text: String = extractSpineAsText(book)
            text = normalizeNewlines(text)

            val index = tmpDir.resolve(INDEX_NAME)
            Files.writeString(index, text, ENCODING, CREATE, TRUNCATE_EXISTING)

            copyImages(book, tmpDir.resolve(TXTZ_IMAGES_FOLDER))

            zipDirectoryDeterministic(tmpDir, txtzOutputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            deleteRecursively(tmpDir)
        }
    }

    private fun readEpub(epubPath: Path): Book {
        BufferedInputStream(Files.newInputStream(epubPath)).use { inStream: InputStream ->
            return EpubReader().readEpub(inStream)
        }
    }

    private fun extractSpineAsText(book: Book?): String {
        requireNotNull(book) { "book cannot be null" }

        val out = StringBuilder(64 * 1024)
        for (ref: SpineReference in book.spine.spineReferences) {
            val res: Resource = ref.resource
                ?: continue

            val charsetName = res.inputEncoding
                ?: "UTF-8"
            val xhtml = String(res.data, Charset.forName(charsetName))
            val bodyText = markdownWriter.processXhtml(xhtml)

            if (bodyText.isNotEmpty()) {
                out.append(bodyText).append(CHAPTER_BREAK)
            }
        }
        return out.toString()
    }

    private fun copyImages(book: Book, imagesBase: Path) {
        Files.createDirectories(imagesBase)

        val resources: Collection<Resource> = book.resources.all
        // itera in ordine deterministico per stabilità degli output
        val images = resources
            .filter { r -> r.mediaType?.name?.startsWith("image/") == true }
            .sortedBy { r -> (r.href ?: "") }

        val usedNames = mutableSetOf<String>()
        for (res in images) {
            var safeName = makeSafeFileName(Paths.get(res.href ?: "image").fileName.toString())
            safeName = ensureUniqueName(safeName, usedNames)
            val out = imagesBase.resolve(safeName)

            BufferedOutputStream(Files.newOutputStream(out, CREATE, TRUNCATE_EXISTING)).use { os ->
                os.write(res.data)
            }
        }
    }

    companion object {
        const val TXTZ_IMAGES_FOLDER: String = "images"
        val ENCODING: Charset = StandardCharsets.UTF_8
        private const val INDEX_NAME: String = "index.txt"
        private const val CHAPTER_BREAK: String = "\n---\n"

        fun normalizeNewlines(s: String): String {
            if (s.isEmpty()) return s

            // prima normalizza CRLF/CR a LF, poi sostituisci il target
            val unified = s.replace("\r\n", "\n")
                .replace('\r', '\n')
            val target = System.lineSeparator()
            return if ("\n" == target) {
                unified
            } else {
                unified.replace("\n", target)
            }
        }

        private fun ensureUniqueName(baseName: String, used: MutableSet<String>): String {
            if (used.add(baseName)) return baseName

            var ext = ""
            var stem = baseName
            val dot = baseName.lastIndexOf('.')
            if (dot > 0) {
                stem = baseName.take(dot)
                ext = baseName.substring(dot)
            }
            var i = 2
            while (!used.add("$stem-$i$ext")) {
                i++
            }
            return "$stem-$i$ext"
        }

        /**
         * Evita caratteri rischiosi e path traversal per nomi file provenienti dagli href dell’epub.
         */
        private fun makeSafeFileName(name: String): String {
            val trimmed = name.trim()
            val sanitized = trimmed
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('|', '_')
                .replace('?', '_')
                .replace('*', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('"', '_')
            return sanitized.ifEmpty { "image" }
        }

        private fun zipDirectoryDeterministic(dir: Path, zipFile: Path) {
            val files = mutableListOf<Path>()
            Files.walk(dir).use { stream ->
                stream
                    .filter { p -> !p.isDirectory() }
                    .forEach { files.add(it) }
            }
            files.sortBy { p -> dir.relativize(p).toString() }

            ZipOutputStream(BufferedOutputStream(Files.newOutputStream(zipFile))).use { zos ->
                for (p in files) {
                    val entryName = dir.relativize(p).toString()
                        .replace(File.separatorChar, '/')
                    val entry = ZipEntry(entryName).apply {
                        time = 0L // timestamp fisso per determinismo dei byte
                    }
                    zos.putNextEntry(entry)
                    Files.copy(p, zos)
                    zos.closeEntry()
                }
            }
        }

        private fun deleteRecursively(dir: Path?) {
            if (dir == null) return
            try {
                if (!dir.exists()) return
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { p ->
                        try {
                            Files.deleteIfExists(p)
                        } catch (_: IOException) {
                        }
                    }
                }
            } catch (_: IOException) {
            }
        }
    }
}
