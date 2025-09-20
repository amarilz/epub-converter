package com.amarildo.epubconverter.viewmodel

import androidx.lifecycle.ViewModel
import com.amarildo.epubconverter.Util.toSnakeCaseFileName
import com.amarildo.epubconverter.service.EpubConverterService
import com.amarildo.epubconverter.service.FileLocator
import com.amarildo.epubconverter.service.TxtzConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

class EpubAutomationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var txtzConverter: TxtzConverter? = null
    private var epubConverterService: EpubConverterService? = null

    suspend fun selectEpub(): String {
        val result: Result<String> = FileLocator().selectFile()
        return try {
            val epubFilePath: String = result.getOrThrow()
            _uiState.value = _uiState.value.copy(
                epubFilePath = epubFilePath,
            )
            return epubFilePath
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Unexpected error: ${e.message}",
            )
            ""
        }
    }

    fun convertEpubToTxtz(epubFilePath: String) {
        // convert to txtz

        try {
            require(!epubFilePath.isBlank()) { "Epub path sould be valued" }

            val epubPath: Path = Paths.get(epubFilePath)
            val snakeCaseFileName: String = toSnakeCaseFileName(epubPath.fileName.toString())
            val txtzOutputPath: Path = Paths.get(epubPath.parent.pathString, "$snakeCaseFileName.txtz")
            txtzConverter = TxtzConverter(epubPath, txtzOutputPath)
            txtzConverter?.convertEpubToTxtz()

            // convert to zip
            epubConverterService = EpubConverterService()
            epubConverterService?.extractTxtzFile(txtzOutputPath)

            _uiState.value = _uiState.value.copy(
                successMessage = "Conversion completed",
            )
        } catch (ex: Exception) {
            _uiState.value = _uiState.value.copy(
                error = ex.message,
            )
        }
    }

    fun clearEpubFilePath() {
        _uiState.update { it.copy(epubFilePath = "") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(isLoading = false, successMessage = null, error = null) }
    }
}
