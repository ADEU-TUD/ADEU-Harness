package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

/** Right click opens the same menu as a long press on Android. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.onRightClick(action: () -> Unit): Modifier =
    onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = action)
