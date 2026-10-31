package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import io.github.thepytales.llmhub.AppContainer

/** Android has no settings pages of its own (Windows: "windows"); false = not handled here. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlatformSettingsSection(c: AppContainer, section: String?): Boolean = false

fun platformSettingsItem(): SettingsItem? = null

/** Einstellungen › Synchronisation (AP-12) is Windows-only for now (Android: AP-28). */
fun syncSettingsItem(): SettingsItem? = null

const val KEY_STORAGE_HINT = "Er wird verschlüsselt auf dem Gerät gespeichert."
const val DEVICE_VOICE = "Gerätestimme"
const val DEVICE_VOICE_TITLE = "Gerätestimme"
const val DEVICE_VOICE_DESCRIPTION = "Android-Sprachausgabe, nur Offline-Stimmen – nichts verlässt das Handy."
const val NO_DEVICE_VOICES = "Keine Offline-Stimmen gefunden. Installiere in den Android-Einstellungen (Sprachausgabe) eine deutsche Stimme."
