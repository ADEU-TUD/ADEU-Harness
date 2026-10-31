package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LearningGame
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.desktop.WebEngine
import java.io.File

/**
 * Plays a learning game walled off (AP-17). Windows: as a tab of the browser beside the chat (AP-14), otherwise in the
 * normal browser – either way as a local file with a Content-Security-Policy that forbids every request.
 */
@Composable
fun GamePlayer(c: AppContainer, game: LearningGame, version: Int?, onDismiss: () -> Unit) {
    LaunchedEffect(game.id, version) {
        val file = c.games.writePlayable(game.id, File(File(Paths.cache, "lernspiele"), "${game.id}-v${version ?: game.versions}.html"), version)
        if (file != null) {
            val uri = file.toURI().toString()
            if (c.settings.current.browserPanel && WebEngine.supported) Browser.open(uri) else DesktopActions.browse(uri)
        } else Notices.error(tr("Das Spiel ließ sich nicht öffnen."))
        onDismiss()
    }
}
