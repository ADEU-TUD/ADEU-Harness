package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File

/** Encrypted with DPAPI: only this Windows user can read the API key and tokens. */
@Suppress("UNUSED_PARAMETER")
fun createSecurePrefs(context: Context): SharedPreferences = FilePrefs.encrypted(File(Paths.data, "secure_settings.dat"))
