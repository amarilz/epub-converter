package com.amarildo.epubconverter.service

import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

open class EpubConverterService {

    private val stagingRoot: Path = Paths.get("/tmp/staging")

    /**
     * Flusso completo:
     * 1) crea una workdir unica
     * 2) salva il .txtz
     * 3) estrae e processa
     * 4) cancella il .txtz
     * 5) zippa TUTTA la workdir e restituisce il ByteArray
     * 6) pulisce la workdir in finally
     */
    fun extractTxtzFile(documentoFirmato: Path) {
        val parentFolder: Path = documentoFirmato.parent
        val pathString: String = documentoFirmato.pathString
        val workdir = stagingRoot.resolve(UUID.randomUUID().toString())

        workdir.createDirectories()

        // Salva il file .txtz nella workdir
        val uploadedTxtz: Path = workdir.resolve(pathString)

        File(pathString).inputStream().use { inStream ->
            Files.copy(inStream, uploadedTxtz, StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            // Estrai il .txtz dentro la workdir
            extractTxtzFile(uploadedTxtz, workdir)

            // Post-process dei file estratti (rinomi, fix riferimenti, ecc.)
            processExtractedFiles(workdir, uploadedTxtz)

            // Cancella il file .txtz originale
            Files.deleteIfExists(uploadedTxtz)

            // Crea lo zip IN MEMORIA con TUTTO il contenuto della workdir
            val outputZip: ByteArray = zipDirectoryToBytes(workdir)
            val get: Path = Paths.get(parentFolder.pathString, "zip_finale.zip")
            Files.write(get, outputZip)
        } finally {
            // Pulisce completamente la workdir, anche in caso di eccezione
            deleteRecursivelyQuiet(workdir)
        }
    }

    // Estrae il .txtz (zip) dentro destDir, con protezione zip-slip
    private fun extractTxtzFile(txtzFile: Path, destDir: Path) {
        ZipInputStream(Files.newInputStream(txtzFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val outPath = destDir.resolve(entry.name).normalize()
                // Zip-slip safety: l'entry deve restare dentro destDir
                if (!outPath.startsWith(destDir)) {
                    throw SecurityException("Zip entry escapes destination directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outPath.createDirectories()
                } else {
                    outPath.parent?.createDirectories()
                    BufferedOutputStream(Files.newOutputStream(outPath)).use { bos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (zipIn.read(buffer).also { read = it } != -1) {
                            bos.write(buffer, 0, read)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    // Post-process nella workdir (non usa più uno staging globale fisso)
    private fun processExtractedFiles(workdir: Path, uploadedTxtz: Path) {
        // 1) elimina metadata.opf (se presente)
        val metadataFile = workdir.resolve("metadata.opf").toFile()
        if (metadataFile.exists() && metadataFile.isFile) {
            metadataFile.delete()
        }

        // 2) rinomina index.txt -> <sanitized>.md
        val indexFile = workdir.resolve("index.txt").toFile()
        val baseName = uploadedTxtz.fileName.toString().replaceFirst("\\.txtz$".toRegex(), "")
        val sanitized = sanitizeFolderName(baseName)

        if (indexFile.exists() && indexFile.isFile) {
            val renamed = workdir.resolve("$sanitized.md").toFile()
            indexFile.renameTo(renamed)

            // 3) rinomina images -> <sanitized>
            val imagesFolder = workdir.resolve("images").toFile()
            val renamedImagesFolder = workdir.resolve(sanitized).toFile()
            if (imagesFolder.exists() && imagesFolder.isDirectory) {
                imagesFolder.renameTo(renamedImagesFolder)
            }

            // 4) crea <sanitized>_optimized.md con riferimenti aggiornati
            val optimized = workdir.resolve("${sanitized}_optimized.md").toFile()
            updateMarkdownImageReferences(renamed, optimized, sanitized)
        }
    }

    // Aggiorna riferimenti immagini nel markdown
    private fun updateMarkdownImageReferences(
        originalFile: File,
        backupFile: File,
        newImagesFolderName: String,
    ) {
        val pattern = Regex("\\]\\(images/(\\S+?\\.[^\\)]+)\\)")
        BufferedReader(FileReader(originalFile)).use { reader ->
            BufferedWriter(FileWriter(backupFile)).use { writer ->
                reader.lineSequence().forEach { line ->
                    val updated =
                        pattern
                            .replace(line, "]($newImagesFolderName/$1)")
                            .replace("\\(", "(")
                            .replace("\\)", ")")
                            .replace("Figur", "figur")
                    writer.write(updated)
                    writer.newLine()
                }
            }
        }
    }

    private fun sanitizeFolderName(folderName: String): String = folderName
        .lowercase()
        .replace("[^a-z0-9]".toRegex(), "_")
        .replace("_+".toRegex(), "_")
        .trim('_')

    // Zippa ricorsivamente l'intera workdir e restituisce ByteArray
    private fun zipDirectoryToBytes(rootDir: Path): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
            Files.walk(rootDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    // Entry path relativo alla root
                    val entryName = rootDir.relativize(file).toString().replace(File.separatorChar, '/')
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    Files.newInputStream(file).use { input ->
                        input.copyTo(zos, 8192)
                    }
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    // Cancellazione ricorsiva silenziosa
    private fun deleteRecursivelyQuiet(path: Path) {
        if (!Files.exists(path)) return
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { p ->
                    try {
                        Files.deleteIfExists(p)
                    } catch (_: IOException) {
                        /* ignora per pulizia best-effort */
                    }
                }
        } catch (_: IOException) {
            /* ignora */
        }
    }
}
