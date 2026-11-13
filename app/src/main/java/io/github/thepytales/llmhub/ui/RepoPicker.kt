package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.data.LinkedRepo
import io.github.thepytales.llmhub.net.RepoSummary

/**
 * Links repositories to a chat like the Claude app: the linked ones under „Ausgewählt“ with a check mark, below
 * all repositories of every stored token (own account and organizations). A tap links or unlinks; changes
 * apply at once. A typed owner/name or GitHub URL can be added too (e.g. a foreign public repository).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPickerSheet(
    c: AppContainer,
    enabled: Boolean,
    selected: List<LinkedRepo>,
    hasToken: Boolean,
    onApply: (enabled: Boolean, repos: List<LinkedRepo>) -> Unit,
    onDismiss: () -> Unit,
) {
    val look = LocalChatLook.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var repos by remember { mutableStateOf<List<RepoSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toolsOn by remember { mutableStateOf(enabled) }
    var chosen by remember { mutableStateOf(selected) }

    LaunchedEffect(hasToken) {
        if (hasToken) {
            try {
                repos = c.github.listRepos()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun apply(next: List<LinkedRepo>, on: Boolean = toolsOn || next.isNotEmpty()) {
        chosen = next
        toolsOn = on
        onApply(on, if (on) next else emptyList())
    }
    fun isChosen(name: String) = chosen.any { it.name.equals(name, ignoreCase = true) }
    fun toggle(name: String, defaultBranch: String?) {
        if (isChosen(name)) apply(chosen.filterNot { it.name.equals(name, ignoreCase = true) }, toolsOn)
        else apply(chosen + LinkedRepo(name, defaultBranch))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = look.sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"))
                }
                Text(
                    if (chosen.isEmpty()) tr("Repositorys") else tr("Repositorys ({0})", chosen.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (!hasToken) {
                Text(
                    tr("Hinterlege zuerst in den Einstellungen ein GitHub-Token, damit das Modell auf deine Repositories zugreifen kann."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Repositorys durchsuchen")) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            val typed = GitHubTools.parseRepo(query)
            val q = query.trim()
            fun matches(name: String) = q.isEmpty() || name.contains(q, ignoreCase = true) ||
                typed != null && name.equals(typed, ignoreCase = true)

            val linked = chosen.filter { matches(it.name) }
            if (linked.isNotEmpty()) {
                SectionLabel(tr("Ausgewählt"))
                RepoRows(linked.map { it.name to it.branch }, checked = true) { name -> toggle(name, null) }
            }

            val all = repos
            when {
                error != null && all == null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
                all == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                else -> {
                    val rest = all.filter { !isChosen(it.fullName) && matches(it.fullName) }
                    if (typed != null && !isChosen(typed) && all.none { it.fullName.equals(typed, ignoreCase = true) }) {
                        SectionLabel(tr("Hinzufügen"))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = look.card,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                                toggle(typed, null)
                                query = ""
                            },
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("„{0}“ verknüpfen", typed), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                                    Text(
                                        tr("Auch fremde öffentliche Repositories"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Outlined.Add, contentDescription = null)
                            }
                        }
                    }
                    if (rest.isNotEmpty()) {
                        SectionLabel(tr("Repositorys"))
                        RepoRows(rest.map { it.fullName to null }, checked = false) { name ->
                            toggle(name, rest.firstOrNull { it.fullName == name }?.defaultBranch)
                        }
                    } else if (linked.isEmpty() && typed == null) {
                        Text(
                            if (q.isEmpty()) tr("Keine Repositories gefunden.") else tr("Kein Repository passt zu „{0}“.", q),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = look.card,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("GitHub-Werkzeuge"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !toolsOn -> tr("Aus – das Modell greift nicht auf GitHub zu")
                                chosen.isEmpty() -> tr("An – ohne festes Repository, das Modell wählt bei Bedarf selbst")
                                chosen.size == 1 -> tr("An – arbeitet in {0}", chosen.first().name)
                                else -> tr("An – arbeitet in allen {0} Repositorys", chosen.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(modifier = Modifier.a11yLabel(tr("GitHub-Werkzeuge")), checked = toolsOn, onCheckedChange = { on -> apply(if (on) chosen else emptyList(), on) })
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** Cards like in the model picker: owner small above the name, check mark for linked repositories. */
@Composable
private fun RepoRows(rows: List<Pair<String, String?>>, checked: Boolean, onToggle: (String) -> Unit) {
    val look = LocalChatLook.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, (fullName, branch) ->
            Surface(
                shape = stackedShape(i, rows.size),
                color = look.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(stackedShape(i, rows.size))
                    .clickable { onToggle(fullName) },
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            fullName.substringBefore('/') + (branch?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            fullName.substringAfter('/'),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (checked) Icon(Icons.Outlined.Check, contentDescription = tr("Ausgewählt"), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
