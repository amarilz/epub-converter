package com.amarildo.epubconverter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "epub-converter",
    ) {
        App()
    }
}