package io.github.thepytales.llmhub.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.GitHubAccount
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.net.accessLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Settings for the further GitHub tokens and API providers; shared by the Android and the Windows app.

@Composable
internal fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) tr("Verbergen") else tr("Anzeigen"),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    val look = LocalChatLook.current
    Card(Modifier.fillMaxWidth(), shape = look.cardShape, border = look.cardBorder, colors = CardDefaults.cardColors(containerColor = look.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** Subtitle of the GitHub entry in the settings overview. */
internal fun gitHubSummary(c: AppContainer): String {
    val s = c.settings.current
    val user = s.githubUser.ifBlank { s.githubAccounts.firstOrNull { it.user.isNotBlank() }?.user.orEmpty() }
    return when {
        s.hasGitHub && s.githubTokenCount > 1 -> tr("Angemeldet als {0} · {1} Tokens", user, s.githubTokenCount)
        s.hasGitHub -> tr("Angemeldet als {0}", user)
        s.githubToken.isNotBlank() || s.githubAccounts.isNotEmpty() -> tr("Token ungültig oder nicht geprüft")
        else -> tr("Kein Token")
    }
}

/** Subtitle of the API entry in the settings overview. */
internal fun apiSummary(c: AppContainer): String {
    val s = c.settings.current
    val extra = s.providers.filter { it.usable }.map { it.name }
    return when {
        s.hasApiKey && extra.isNotEmpty() -> "ScaDS.AI + ${extra.joinToString(", ")}"
        s.hasApiKey -> tr("API-Key hinterlegt")
        extra.isNotEmpty() -> tr("Nur {0} – ScaDS.AI-Key fehlt", extra.joinToString(", "))
        else -> tr("API-Key fehlt")
    }
}

/**
 * Further GitHub tokens below the main one. A fine-grained token only reaches the repositories of one owner,
 * so working in the own account and two organizations needs three tokens; the app picks the right one per repository.
 */
@Composable
fun GitHubAccountsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    Section(tr("Weitere Tokens (Organisationen)")) {
        Text(
            tr("Ein Fine-grained Token gilt immer nur für einen Besitzer – dein Konto oder eine Organisation. " +
                "Lege für jede Organisation ein eigenes Token an (beim Erstellen unter „Resource owner“ die Organisation wählen) ") +
                tr("und trage es hier ein. Die App zeigt die Repositories aller Tokens zusammen und nimmt bei jeder Aktion " +
                "automatisch das passende."),
            style = MaterialTheme.typography.bodySmall,
        )
        s.githubAccounts.forEachIndexed { i, account ->
            if (i > 0) HorizontalDivider()
            GitHubAccountRow(c, i + 2, account)
        }
        OutlinedButton(onClick = { c.settings.update { it.copy(githubAccounts = it.githubAccounts + GitHubAccount()) } }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(tr("Token hinzufügen"), Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun GitHubAccountRow(c: AppContainer, number: Int, account: GitHubAccount) {
    val scope = rememberCoroutineScope()
    var status by remember(account.id) { mutableStateOf<String?>(null) }
    var testing by remember(account.id) { mutableStateOf(false) }
    suspend fun check() {
        testing = true
        status = try {
            c.github.verifyAccount(account.id)
        } catch (e: Exception) {
            "✗ ${e.message}"
        }
        testing = false
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SecretField(tr("GitHub-Token {0}", number), account.token) { v ->
                // A changed token has to be verified again before it is used for the GitHub options.
                c.settings.update { s ->
                    s.copy(githubAccounts = s.githubAccounts.map {
                        if (it.id != account.id || it.token == v.trim()) it else it.copy(token = v.trim(), user = "", owners = emptyList())
                    })
                }
            }
        }
        IconButton(onClick = { c.settings.update { s -> s.copy(githubAccounts = s.githubAccounts.filter { it.id != account.id }) } }) {
            Icon(Icons.Outlined.Delete, contentDescription = tr("Token entfernen"))
        }
    }
    // Checks a newly entered token automatically, once typing has paused.
    LaunchedEffect(account.token) {
        if (account.token.isBlank()) {
            status = null
        } else if (account.user.isBlank()) {
            delay(800)
            check()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = !testing && account.token.isNotBlank(), onClick = { scope.launch { check() } }) { Text(tr("Token testen")) }
        if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
    }
    (status ?: account.user.ifBlank { null }?.let { accessLine(it, account.owners) })?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * API keys of further providers below the main one (ScaDS.AI). Their chat models appear in the model picker
 * below the ones of ScaDS.AI; for Gemini the recommended Flash models are shown at the top.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiProvidersSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    fun add(kind: ProviderKind) = c.settings.update {
        it.copy(providers = it.providers + ApiProvider(kind = kind.name, name = kind.defaultName, baseUrl = kind.defaultUrl))
    }
    // Loads the models of a newly entered key once typing has paused.
    val keys = s.providers.filter { it.usable }.joinToString { it.id + it.apiKey + it.baseUrl }
    LaunchedEffect(keys) {
        if (keys.isNotEmpty()) {
            delay(800)
            c.models.refresh()
        }
    }
    Section(tr("Weitere Anbieter")) {
        Text(
            tr("Zusätzliche API-Keys, z.B. für Google Gemini (kostenloser Key aus Google AI Studio). Ihre Modelle erscheinen " +
                "in der Modellauswahl unter denen von ScaDS.AI. Vorlesen, Spracheingabe, Bilder und die Wissenssuche " +
                "laufen weiter über ScaDS.AI."),
            style = MaterialTheme.typography.bodySmall,
        )
        s.providers.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider()
            ProviderRow(c, p)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.providers.none { it.providerKind == ProviderKind.GEMINI }) {
                OutlinedButton(onClick = { add(ProviderKind.GEMINI) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(tr("Gemini hinzufügen"), Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(onClick = { add(ProviderKind.OPENAI) }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(tr("Anderer Anbieter"), Modifier.padding(start = 6.dp))
            }
        }
        if (s.providers.any { it.providerKind == ProviderKind.GEMINI }) {
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text(tr("Gemini-Key in Google AI Studio erstellen")) }
        }
    }
}

@Composable
private fun ProviderRow(c: AppContainer, p: ApiProvider) {
    val scope = rememberCoroutineScope()
    var status by remember(p.id) { mutableStateOf<String?>(null) }
    var testing by remember(p.id) { mutableStateOf(false) }
    fun edit(transform: (ApiProvider) -> ApiProvider) = c.settings.update { s ->
        s.copy(providers = s.providers.map { if (it.id == p.id) transform(it) else it })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name.ifBlank { p.providerKind.label }, style = MaterialTheme.typography.titleSmall)
                // Wohin die Daten gehen (AP-119): a server on this computer (e.g. Ollama) is local, others by their address.
                DataPlaceChip(io.github.thepytales.llmhub.data.DataPlace.ofUrl(p.baseUrl))
            }
            Text(p.providerKind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            c.settings.update { s -> s.copy(providers = s.providers.filter { it.id != p.id }) }
            c.models.refresh()
        }) {
            Icon(Icons.Outlined.Delete, contentDescription = tr("Anbieter entfernen"))
        }
    }
    if (p.providerKind == ProviderKind.OPENAI) {
        OutlinedTextField(
            value = p.name,
            onValueChange = { v -> edit { it.copy(name = v) } },
            label = { Text(tr("Name")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = p.baseUrl,
            onValueChange = { v -> edit { it.copy(baseUrl = v.trim()) } },
            label = { Text(tr("API-Basis-URL (OpenAI-kompatibel)")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SecretField(tr("API-Key"), p.apiKey) { v -> edit { it.copy(apiKey = v.trim()) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            enabled = !testing && p.usable,
            onClick = {
                testing = true
                scope.launch {
                    status = try {
                        val ids = c.llm.listModels(c.settings.current.providers.first { it.id == p.id })
                        c.models.refresh()
                        tr("✓ Verbunden – {0} Modelle verfügbar", ids.size)
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                    testing = false
                }
            },
        ) { Text(tr("Verbindung testen")) }
        if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
