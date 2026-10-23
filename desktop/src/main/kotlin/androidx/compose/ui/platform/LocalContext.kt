package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.thepytales.llmhub.LlmHubApp

/** Stand-in for Android's LocalContext, so shared screens can keep using it on Windows. */
val LocalContext = staticCompositionLocalOf<Context> { LlmHubApp.context }
