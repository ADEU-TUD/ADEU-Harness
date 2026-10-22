package io.github.thepytales.llmhub

import android.app.Application
import android.content.Context
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.audio.AudioPlayer
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.ModelsRepository
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val llm = LlmClient(settings)
    val github = GitHubClient(settings, llm.http)
    val githubTools = GitHubTools(github)
    val documentTools = DocumentTools(context.filesDir)
    val webTools = WebTools(llm.http)
    val chats = ChatRepository(context)
    val models = ModelsRepository(llm)
    val player = AudioPlayer(context)
}

class LlmHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
