package com.amarildo.epubconverter.service

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileLocator {

    suspend fun selectFile(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val selectedDirectory = FileKit.openFilePicker()
            if (selectedDirectory != null) {
                Result.success(selectedDirectory.file.absolutePath)
            } else {
                Result.failure(kotlinx.io.IOException("No directory selected"))
            }
        } catch (e: Exception) {
            Result.failure(kotlinx.io.IOException("Error on selecting the folder: ${e.message}", e))
        }
    }
}
