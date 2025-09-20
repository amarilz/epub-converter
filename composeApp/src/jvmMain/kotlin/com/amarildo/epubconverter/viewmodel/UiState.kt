package com.amarildo.epubconverter.viewmodel

data class UiState(
    val epubFilePath: String = "",
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)
