package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.DataPlace
import io.github.thepytales.llmhub.data.Places
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.data.Role
import io.github.thepytales.llmhub.data.Roles
import io.github.thepytales.llmhub.data.withTool
import kotlinx.coroutines.launch

/**
 * Lora einrichten Schritt für Schritt (AP-120): a friendly setup of everything that matters – at the first start as a
 * window, later through Lora („Lora einrichten“), Strg+K or Einstellungen › Lora. Written for people without technical
 * background: one decision per step, short sentences, the recommendation chosen, everything skippable and changeable
 * later; expert features (MCP-Server, Server-Terminal) are folded away under „Für Fortgeschrittene“. „Alles auf diesem
 * Gerät“ is offered, but clearly as the special case it is (very sensitive data, working without a server) and hands
 * over to the local setup (AP-112). Every choice is saved at once; *Später* keeps the step ([AppSettings.setupStep]).
 */
enum class SetupPage(val title: String, val icon: ImageVector) {
    WELCOME(trLater("Willkommen"), Icons.Outlined.WavingHand),
    ROLES(trLater("Wofür"), Icons.Outlined.Work),
    PLACE(trLater("Wo Lora arbeitet"), Icons.Outlined.Place),
    ACCESS(trLater("Zugang"), Icons.Outlined.VpnKey),
    PRIVACY(trLater("Datenschutz"), Icons.Outlined.Security),
    TOOLS(trLater("Werkzeuge"), Icons.Outlined.Build),
    LOOK(trLater("Aussehen"), Icons.Outlined.Palette),
    DONE(trLater("Fertig"), Icons.Outlined.Flag),
}

/** How Lora reaches language models – the big decision of the setup. */
enum class SetupWay { TU, OWN, LOCAL }

/** The way the settings describe now: everything local, a key of another provider only, or the Uni server. */
fun currentWay(c: AppContainer): SetupWay {
    val s = c.settings.current
    return when {
        s.loraPlace == "LOCAL" && !s.hasApiKey -> SetupWay.LOCAL
        !s.hasApiKey && s.providers.any { it.usable } -> SetupWay.OWN
        else -> SetupWay.TU
    }
}

/** First start as a window (replaces the questions of AP-80/AP-71; they live on inside the steps). */
@Composable
fun SetupWizardDialog(c: AppContainer, onNavigate: (String) -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(0.94f).heightIn(max = 720.dp).fillMaxHeight(0.92f)) {
            SetupWizard(c, onClose = {}, onNavigate = onNavigate)
        }
    }
}

/** The setup on a screen of its own (route „setup“, from Lora, Strg+K and the settings). */
@Composable
fun SetupScreen(c: AppContainer, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    androidx.compose.material3.Scaffold { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(Modifier.widthIn(max = 960.dp).fillMaxSize()) { SetupWizard(c, onClose = onBack, onNavigate = onNavigate) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizard(c: AppContainer, onClose: () -> Unit, onNavigate: (String) -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val pages = SetupPage.entries
    var way by remember { mutableStateOf(currentWay(c)) }
    // The step to show: continued where „Später“ left it.
    var index by remember { mutableStateOf((s.setupStep - 1).coerceIn(0, pages.size - 1)) }
    val models = remember { IntroModelChoice(c) }
    val page = pages[index]
    fun visible(p: SetupPage) = !(p == SetupPage.ACCESS && way == SetupWay.LOCAL)
    fun go(i: Int) {
        var n = i.coerceIn(0, pages.size - 1)
        while (!visible(pages[n]) && n in 1 until pages.size - 1) n += if (i >= index) 1 else -1
        index = n
        c.settings.update { it.copy(setupStep = n + 1) }
    }
    fun later() {
        // The first-start window does not come back; the setup can be continued from Lora, Strg+K or the settings.
        c.settings.update { it.copy(loraIntroDone = true, setupStep = index + 1) }
        onClose()
    }
    fun finish() {
        models.place = when (way) {
            SetupWay.LOCAL -> "LOCAL"
            else -> if (models.place == "LOCAL") "MIXED" else models.place
        }
        models.apply(c)
        if (way != SetupWay.LOCAL && c.settings.current.places.isNotBlank() && Places.profile(c) == Places.Profile.ALL_LOCAL) Places.apply(c, Places.Profile.MIXED)
        c.settings.update { it.copy(loraIntroDone = true, modelsIntroDone = true, setupStep = 0) }
        Notices.info(tr("Lora ist eingerichtet. Alles lässt sich unter Einstellungen ändern."))
        onClose()
        if (way == SetupWay.LOCAL) onNavigate("local-setup")
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                // The steps on the left: done ones ticked, each one reachable.
                Column(Modifier.width(220.dp).fillMaxHeight().padding(start = 16.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr("Lora einrichten"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    pages.filter(::visible).forEach { p ->
                        val i = pages.indexOf(p)
                        val current = i == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().clickable { go(i) },
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (i < index) Icons.Outlined.CheckCircle else p.icon, null, Modifier.size(20.dp), tint = if (i < index) LocalChatLook.current.success else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(tr(p.title), style = MaterialTheme.typography.bodyMedium, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                androidx.compose.material3.VerticalDivider()
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val shown = pages.filter(::visible)
                    val pos = shown.indexOf(page) + 1
                    if (!wide) {
                        Text(tr("Schritt {0} von {1}", pos, shown.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { pos.toFloat() / shown.size }, modifier = Modifier.fillMaxWidth())
                    }
                    AnimatedContent(targetState = page, label = "einrichtung") { p ->
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            when (p) {
                                SetupPage.WELCOME -> WelcomePage(c)
                                SetupPage.ROLES -> RolesPage(c, models)
                                SetupPage.PLACE -> PlacePage(c, way) { way = it }
                                SetupPage.ACCESS -> AccessPage(c, way)
                                SetupPage.PRIVACY -> PrivacyPage(c, models)
                                SetupPage.TOOLS -> ToolsPage(c, onNavigate = { r -> later(); onNavigate(r) })
                                SetupPage.LOOK -> LookPage(c)
                                SetupPage.DONE -> DonePage(c, way, models) { go(pages.indexOf(it)) }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::later) { Text(tr("Später")) }
                    Spacer(Modifier.weight(1f))
                    if (index > 0) TextButton(onClick = { go(index - 1) }) { Text(tr("Zurück")) }
                    Spacer(Modifier.width(8.dp))
                    when (page) {
                        SetupPage.WELCOME -> Button(onClick = { go(index + 1) }) { Text(tr("Los geht’s")) }
                        SetupPage.DONE -> Button(onClick = ::finish) { Text(if (way == SetupWay.LOCAL) tr("Fertig – weiter zu den Modellen") else tr("Fertig")) }
                        else -> Button(onClick = { go(index + 1) }) { Text(tr("Weiter")) }
                    }
                }
            }
        }
    }
}

// ── Pieces ──

@Composable
private fun PageHead(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A big choice: title, one or two lines, optional chip on the right; ticked when [selected]. */
@Composable
private fun ChoiceCard(title: String, text: String, selected: Boolean, chip: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else LocalChatLook.current.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = tr("Ausgewählt"), tint = MaterialTheme.colorScheme.primary)
                else Surface(shape = CircleShape, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(18.dp)) {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    chip?.invoke()
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CheckLine(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A part folded away for experienced users. */
@Composable
private fun Expert(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) tr("Einklappen") else tr("Aufklappen"))
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

// ── Steps ──

@Composable
private fun WelcomePage(c: AppContainer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoraFigure(Modifier.size(92.dp, 64.dp), label = tr("Lora winkt"), director = rememberLoraDirector(listOf(LoraAction.WAVE, LoraAction.LOOK), LoraAction.WAVE))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(tr("Hallo, ich bin Lora!"), style = MaterialTheme.typography.headlineSmall)
            Text(tr(io.github.thepytales.llmhub.Brand.SLOGAN), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(tr("Ich richte die App jetzt mit dir ein – in wenigen Minuten, Schritt für Schritt."), style = MaterialTheme.typography.bodyLarge)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            tr("Eine Frage je Schritt – meine Empfehlung ist schon ausgewählt."),
            tr("Du kannst jeden Schritt überspringen und alles später unter Einstellungen ändern."),
            tr("„Später“ unterbricht – du machst dann an derselben Stelle weiter (sag mir einfach „Lora einrichten“)."),
        ).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp).padding(top = 2.dp), tint = LocalChatLook.current.success)
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RolesPage(c: AppContainer, models: IntroModelChoice) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val roles = Role.parse(s.roles)
    PageHead(tr("Wofür nutzt du Lora vor allem?"), tr("Mehrere sind möglich. Danach schlage ich passende Werkzeuge und Vorlagen vor."))
    Role.entries.forEach { r ->
        ChoiceCard(tr(r.label), tr(r.description), r in roles) {
            val next = if (r in roles) roles - r else roles + r
            c.settings.update { cur -> if (r in roles) cur.copy(roles = Role.format(next)) else Roles.apply(cur, next, IS_ANDROID) }
            if (r !in roles) Roles.packs(setOf(r)).forEach { p -> if (p !in models.packs) models.packs += p }
        }
    }
}

@Composable
private fun PlacePage(c: AppContainer, way: SetupWay, onWay: (SetupWay) -> Unit) {
    val hw by c.localLlm.hardware.collectAsStateWithLifecycle()
    PageHead(tr("Wo soll ich arbeiten?"), tr("Hier entscheidest du, wohin deine Texte gehen, wenn ich oder ein Chat antworte."))
    ChoiceCard(
        tr("Uni-Server der TU Dresden (empfohlen)"),
        tr("Schnell und stark. Deine Daten bleiben an der TU Dresden (ScaDS.AI). Du brauchst dafür einen kostenlosen Schlüssel – ich zeige dir im nächsten Schritt, wie."),
        way == SetupWay.TU, chip = { DataPlaceChip(DataPlace.TU) },
    ) { onWay(SetupWay.TU) }
    ChoiceCard(
        tr("Alles auf diesem Gerät"),
        tr("Nur sinnvoll für sehr sensible Daten oder wenn Lora ganz ohne Server arbeiten soll. Langsamer und schlichter als der Uni-Server, braucht einen starken Rechner und mehrere GB Speicher für die Modelle."),
        way == SetupWay.LOCAL, chip = { DataPlaceChip(DataPlace.LOCAL) },
    ) { onWay(SetupWay.LOCAL) }
    if (way == SetupWay.LOCAL) {
        val h = hw
        val verdict = when {
            h == null -> tr("Ich prüfe dieses Gerät, sobald du die Modelle lädst.")
            h.ramGb < 12 -> tr("Dieses Gerät hat {0} GB Arbeitsspeicher – das reicht nur für kleine Modelle. Für die meisten Aufgaben ist der Uni-Server deutlich besser.", h.ramGb.toInt())
            h.ramGb < 24 -> tr("Dieses Gerät hat {0} GB Arbeitsspeicher – kleine und mittlere Modelle gehen, Antworten dauern länger.", h.ramGb.toInt())
            else -> tr("Dieses Gerät hat {0} GB Arbeitsspeicher – gut geeignet.", h.ramGb.toInt())
        }
        HintText(verdict + " " + tr("Nach dem letzten Schritt führe ich dich durch das Laden der Modelle."))
    }
    Expert(tr("Für Fortgeschrittene: eigener Anbieter")) {
        ChoiceCard(
            tr("Eigener Anbieter (z. B. Google Gemini)"),
            tr("Mit deinem eigenen Schlüssel. Deine Texte gehen dann an diesen Anbieter außerhalb der TU – nicht für personenbezogene oder vertrauliche Daten."),
            way == SetupWay.OWN, chip = { DataPlaceChip(DataPlace.CLOUD) },
        ) { onWay(SetupWay.OWN) }
    }
}

@Composable
private fun AccessPage(c: AppContainer, way: SetupWay) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    fun test(provider: ApiProvider?) {
        testing = true
        status = null
        scope.launch {
            status = try {
                val n = c.llm.listModels(provider).size
                c.models.refresh()
                tr("✓ Das klappt – {0} Modelle verfügbar.", n)
            } catch (e: Exception) {
                tr("✗ Das hat noch nicht geklappt: {0}", e.message ?: e.javaClass.simpleName)
            }
            testing = false
        }
    }
    if (way == SetupWay.OWN) {
        val gemini = s.providers.firstOrNull { it.providerKind == ProviderKind.GEMINI }
        PageHead(tr("Schlüssel für Google Gemini"), tr("Den Schlüssel erzeugst du kostenlos in Google AI Studio und fügst ihn hier ein."))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DataPlaceChip(DataPlace.CLOUD)
            Text(tr("Deine Texte gehen an Google."), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text(tr("Google AI Studio öffnen")) }
        SecretField(tr("Gemini-Schlüssel"), gemini?.apiKey.orEmpty()) { v ->
            c.settings.update { cur ->
                val p = cur.providers.firstOrNull { it.providerKind == ProviderKind.GEMINI } ?: ApiProvider()
                cur.copy(providers = cur.providers.filter { it.id != p.id } + p.copy(apiKey = v.trim()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !testing && gemini?.usable == true, onClick = { test(c.settings.current.providers.first { it.providerKind == ProviderKind.GEMINI }) }) { Text(tr("Schlüssel prüfen")) }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        HintText(tr("Andere OpenAI-kompatible Anbieter trägst du unter Einstellungen › API-Zugang ein."))
    } else {
        PageHead(tr("Dein Schlüssel für den Uni-Server"), tr("Einmal einrichten, dann merkt sich die App den Schlüssel verschlüsselt auf diesem Gerät."))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberedStep(1, tr("Im Self-Service-Portal der TU Dresden anmelden und unter „ScaDS.AI LLM“ einen Schlüssel erzeugen. Er beginnt mit „sk-“."))
            OutlinedButton(onClick = { uri.openUri("https://selfservice.tu-dresden.de/") }, modifier = Modifier.padding(start = 36.dp)) { Text(tr("Self-Service-Portal öffnen")) }
            NumberedStep(2, tr("Den Schlüssel kopieren und hier einfügen:"))
            Box(Modifier.padding(start = 36.dp)) { SecretField(tr("API-Schlüssel"), s.apiKey) { v -> c.settings.update { it.copy(apiKey = v.trim()) } } }
            NumberedStep(3, tr("Prüfen, ob alles klappt:"))
            Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !testing && s.apiKey.isNotBlank(), onClick = { test(null) }) { Text(tr("Schlüssel prüfen")) }
                if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
            }
        }
        HintText(tr("Noch keinen Schlüssel? Kein Problem – „Weiter“ und später unter Einstellungen › API-Zugang eintragen."))
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.startsWith("✓")) LocalChatLook.current.success else MaterialTheme.colorScheme.error) }
}

@Composable
private fun NumberedStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PrivacyPage(c: AppContainer, models: IntroModelChoice) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    PageHead(tr("Datenschutz"), tr("Bevor etwas an einen Server geht, kann ich Namen, Adressen und andere persönliche Angaben durch Platzhalter ersetzen. In der Antwort setze ich sie wieder ein."))
    ChoiceCard(tr("Namen vor dem Senden ersetzen (empfohlen)"), tr("Aus „Frau Müller aus Pirna“ wird für den Server „Person 1 aus Ort 1“. Du siehst immer die echten Namen."), s.pseudonymize) {
        c.settings.update { it.copy(pseudonymize = !it.pseudonymize) }
    }
    if (models.offerPii) CheckLine(
        tr("Namen noch zuverlässiger finden (Modell, {0} MB)", io.github.thepytales.llmhub.data.PiiModelInfo.MEGABYTES),
        tr("Ein kleines Erkennungsmodell auf diesem Gerät findet auch ungewöhnliche Namen, Schulen und Orte."),
        models.pii,
    ) { models.pii = it }
    HintText(tr("Für besonders sensible Forschung gibt es den Tresor (Werkzeuge) und Projekte mit der Stufe „Nur TU Dresden“ oder „Nur auf diesem Gerät“."))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsPage(c: AppContainer, onNavigate: (String) -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val tools = remember { AppTool.available(IS_ANDROID) }
    val everyday = tools.filter { it != AppTool.SERVER }
    PageHead(tr("Welche Werkzeuge möchtest du sehen?"), tr("Chat, Dokumente, Dateien und Vorlagen sind immer da. Diese Werkzeuge kommen dazu – angehakt sind die, die zu deinen Aufgaben passen."))
    everyday.forEach { t ->
        CheckLine(t.label, t.description.substringBefore(". ").substringBefore(": ").trimEnd('.') + ".", t.id in s.enabledToolIds) { on ->
            c.settings.update { it.withTool(t, on) }
        }
    }
    Expert(tr("Für Fortgeschrittene")) {
        if (AppTool.SERVER in tools) CheckLine(tr("Server-Terminal"), tr("Webserver über SSH pflegen, Lora schlägt Schritte vor – nur wer selbst Server betreut."), AppTool.SERVER.id in s.enabledToolIds) { on ->
            c.settings.update { it.withTool(AppTool.SERVER, on) }
        }
        Text(tr("MCP-Server und weitere Anbindungen (Nextcloud, GitHub, Zotero) richtest du unter Einstellungen › Anbindungen ein."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onNavigate("settings/connections") }) { Text(tr("Anbindungen öffnen")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPage(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    PageHead(tr("Wie soll die App aussehen?"), tr("Hell oder dunkel, Schriftgröße und wo ich erscheine."))
    GroupLabel(tr("Farben"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m -> FilterChip(selected = s.themeMode == m.name, onClick = { c.settings.update { it.copy(themeMode = m.name) } }, label = { Text(tr(m.label)) }) }
    }
    GroupLabel(tr("Schriftgröße"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.9f to tr("Klein"), 1f to tr("Normal"), 1.15f to tr("Groß"), 1.3f to tr("Sehr groß")).forEach { (scale, label) ->
            FilterChip(selected = s.textScale == scale, onClick = { c.settings.update { it.copy(textScale = scale) } }, label = { Text(label) })
        }
    }
    GroupLabel(tr("Wo soll ich erscheinen?"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LORA_MODES.forEach { (id, label) -> FilterChip(selected = s.loraMode == id, onClick = { c.settings.update { it.copy(loraMode = id, hideLora = id == "OFF") } }, label = { Text(tr(label)) }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { c.settings.update { it.copy(loraCalm = !it.loraCalm) } }) {
        Column(Modifier.weight(1f)) {
            Text(tr("Ruhigere Bewegungen"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Ich laufe, tanze und fliege dann nicht, sondern lese und schreibe nur."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = s.loraCalm, onCheckedChange = { v -> c.settings.update { it.copy(loraCalm = v) } })
    }
}

@Composable
private fun DonePage(c: AppContainer, way: SetupWay, models: IntroModelChoice, onChange: (SetupPage) -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    PageHead(tr("Fast geschafft!"), tr("Das ist eingerichtet. Ein Tipp auf „Ändern“ springt zum Schritt."))
    val roles = Role.parse(s.roles).joinToString(", ") { tr(it.label) }.ifEmpty { tr("keine gewählt") }
    val tools = AppTool.available(IS_ANDROID).filter { it.id in s.enabledToolIds }.joinToString(", ") { it.label }.ifEmpty { tr("keine zusätzlichen") }
    val place = when (way) {
        SetupWay.TU -> tr("Uni-Server der TU Dresden")
        SetupWay.OWN -> tr("Eigener Anbieter")
        SetupWay.LOCAL -> tr("Alles auf diesem Gerät")
    }
    val key = when {
        way == SetupWay.LOCAL -> tr("nicht nötig")
        s.hasAnyApiKey -> tr("eingetragen")
        else -> tr("noch nicht eingetragen – später unter Einstellungen › API-Zugang")
    }
    val dataPlace = when (way) { SetupWay.TU -> DataPlace.TU; SetupWay.OWN -> DataPlace.CLOUD; SetupWay.LOCAL -> DataPlace.LOCAL }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Summary(tr("Wofür"), roles) { onChange(SetupPage.ROLES) }
        Summary(tr("Wo Lora arbeitet"), place, chip = { DataPlaceChip(dataPlace) }) { onChange(SetupPage.PLACE) }
        if (way != SetupWay.LOCAL) Summary(tr("Schlüssel"), key) { onChange(SetupPage.ACCESS) }
        Summary(tr("Namen ersetzen"), if (s.pseudonymize) tr("an") else tr("aus")) { onChange(SetupPage.PRIVACY) }
        Summary(tr("Werkzeuge"), tools) { onChange(SetupPage.TOOLS) }
        Summary(tr("Aussehen"), tr(ThemeMode.entries.firstOrNull { it.name == s.themeMode }?.label ?: "System") + " · " + tr(LORA_MODES.firstOrNull { it.first == s.loraMode }?.second ?: "")) { onChange(SetupPage.LOOK) }
    }
    if (models.offerCommand && way != SetupWay.LOCAL) CheckLine(
        tr("Befehle sofort verstehen (Modell, {0} MB)", io.github.thepytales.llmhub.data.CommandModelInfo.MEGABYTES),
        tr("Damit erkenne ich einfache Wünsche wie „PDF zusammenfügen“ sofort und ohne Server."),
        models.command,
    ) { models.command = it }
    if (way == SetupWay.LOCAL) HintText(tr("Nach „Fertig“ lädt die Einrichtung „Alles lokal“ die passenden Modelle für dieses Gerät – mit Fortschritt, jederzeit unterbrechbar."))
    else HintText(tr("Tipp: Frag mich jederzeit, z. B. „Wie schreibe ich einen Antrag?“ oder „Mach das Design dunkel“ – ich finde die passende Stelle."))
}

@Composable
private fun Summary(label: String, value: String, chip: (@Composable () -> Unit)? = null, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = LocalChatLook.current.success)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                chip?.invoke()
            }
        }
        TextButton(onClick = onChange) { Text(tr("Ändern")) }
    }
}
