package com.amarildo.epubconverter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.amarildo.epubconverter.view.EpubAutomation

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EpubConverter",
    ) {
        EpubAutomation()
    }
}
