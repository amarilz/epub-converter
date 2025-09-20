package com.amarildo.epubconverter

object Util {

    fun toSnakeCaseFileName(input: String): String {
        val nameWithoutExtension = input.substringBeforeLast('.')
        val snakeCase = StringBuilder()
        var lastCharIsUnderscore = false

        for (char in nameWithoutExtension) {
            if (char.isLetter()) {
                snakeCase.append(char.lowercaseChar())
                lastCharIsUnderscore = false
            } else {
                if (!lastCharIsUnderscore && snakeCase.isNotEmpty()) {
                    snakeCase.append('_')
                    lastCharIsUnderscore = true
                }
            }
        }
        return snakeCase.toString().removeSuffix("_")
    }
}
