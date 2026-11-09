package com.amarildo.epubconverter

import java.util.Properties

internal object AppVersion {
    val value: String by lazy {
        val properties = Properties()
        val versionResource = requireNotNull(
            AppVersion::class.java.getResourceAsStream("/app-version.properties"),
        ) {
            "Application version resource is missing"
        }

        versionResource.use(properties::load)
        requireNotNull(properties.getProperty("appVersion")) {
            "Application version is missing from app-version.properties"
        }
    }
}
