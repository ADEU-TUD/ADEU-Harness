package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalVision
import io.github.thepytales.llmhub.local.Purpose
import io.github.thepytales.llmhub.local.DeviceBench
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Where an area of the app works (AP-112): on this device, mixed, or over an API (Uni-Server or another provider). */
enum class Place(val label: String) {
    LOCAL(trLater("Lokal")),
    MIXED(trLater("Gemischt")),
    API(trLater("API")),
}

/**
 * The areas of „Wo Lora arbeitet“ (AP-112) with the places they can have. [fixed] areas have one place only (the
 * Postfach on this device, Bilder erzeugen only over an API …); [reasons] say why a place is not possible.
 */
enum class Area(val title: String, val what: String, val places: Set<Place>, val reasons: Map<Place, String> = emptyMap()) {
    LORA(trLater("Lora"), trLater("Antworten, Werkzeuge und Abläufe"), Place.entries.toSet()),
    CHAT(
        trLater("Chat"), trLater("Fragen, Schreiben, Zusammenfassen, Übersetzen, Code"), setOf(Place.LOCAL, Place.API),
        mapOf(Place.MIXED to trLater("Ein Chat nutzt ein Modell – im Chat kannst du es jederzeit wechseln.")),
    ),
    MAIL(
        trLater("Postfach"), trLater("Mails lesen, sortieren, Entwürfe"), setOf(Place.LOCAL),
        mapOf(Place.MIXED to trLater("Ins Postfach darf nur ein Modell auf diesem Gerät (fest)."), Place.API to trLater("Ins Postfach darf nur ein Modell auf diesem Gerät (fest).")),
    ),
    DOCS(
        trLater("Dokumente, Tabellen, Folien"), trLater("Bearbeiten immer hier; Lora schreibt mit"), setOf(Place.LOCAL, Place.API),
        mapOf(Place.MIXED to trLater("Ein Modell je Dokument-Hilfe – Lokal oder API.")),
    ),
    TRANSCRIPTION(trLater("Transkription"), trLater("Aufnahmen und Interviews in Text"), Place.entries.toSet()),
    DICTATION(trLater("Spracheingabe und Diktat"), trLater("Sprechen statt tippen, im Chat und bei Lora"), Place.entries.toSet()),
    SEARCH(trLater("Suche nach Bedeutung"), trLater("Wissenssammlung und Projekte"), Place.entries.toSet()),
    SPEECH(
        trLater("Vorlesen und Sprachmodus"), trLater("Antworten vorlesen"), setOf(Place.LOCAL, Place.API),
        mapOf(Place.MIXED to trLater("Eine Stimme für alles – Lokal oder API.")),
    ),
    VISION(trLater("Bilder verstehen"), trLater("Bilder im Chat, Screenshots, Scans, Texterkennung"), Place.entries.toSet()),
    NAMES(
        trLater("Pseudonymisierung"), trLater("Namen ersetzen, bevor etwas hinausgeht"), setOf(Place.LOCAL),
        mapOf(Place.MIXED to trLater("Läuft immer auf diesem Gerät – sonst sähe der Server die Namen."), Place.API to trLater("Läuft immer auf diesem Gerät – sonst sähe der Server die Namen.")),
    ),
    COMMANDS(
        trLater("Befehle verstehen"), trLater("Bereich öffnen, Einstellung umstellen"), setOf(Place.LOCAL),
        mapOf(Place.MIXED to trLater("Lora Befehle läuft immer auf diesem Gerät."), Place.API to trLater("Lora Befehle läuft immer auf diesem Gerät.")),
    ),
    IMAGES(
        trLater("Bilder erzeugen"), trLater("Bilder und Grafiken aus einer Beschreibung"), setOf(Place.API),
        mapOf(Place.LOCAL to trLater("Nur über API – kein Bildmodell auf dem Gerät (Entscheidung 10.11.2026)."), Place.MIXED to trLater("Nur über API – kein Bildmodell auf dem Gerät (Entscheidung 10.11.2026).")),
    ),
    WEB(
        trLater("Web, Literatur, Nextcloud, Updates"), trLater("Websuche, Quellen, Bibliotheken, Synchronisierung"), emptySet(),
        mapOf(
            Place.LOCAL to trLater("Braucht Internet, aber keinen API-Schlüssel."), Place.MIXED to trLater("Braucht Internet, aber keinen API-Schlüssel."),
            Place.API to trLater("Braucht Internet, aber keinen API-Schlüssel."),
        ),
    ),
    ;

    /** The area has a choice at all (not fixed to one place, not only Internet). */
    val choosable get() = places.size > 1
}

/**
 * Where a request may go (AP-112), set around requests of other areas than the chat – Lora, Dokumente, Transkription,
 * Bilder verstehen. [LlmClient][io.github.thepytales.llmhub.net.LlmClient] reads it; without it a request counts as Chat.
 */
class PlaceScope(val area: Area) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PlaceScope>
}

/** A request that must stay on this device found nothing here to run it – the message says what to load (AP-112). */
class OnDeviceOnly(message: String) : IOException(message)

/**
 * „Wo Lora arbeitet“ (AP-112): the place of each area, what runs on this device and why not, the quick choice
 * (Alles lokal · Gemischt · Alles über API) and the gate that keeps requests on the device. Lora, Vorlesen, Transkription
 * and Suche keep their own settings (they are read and written here); Chat, Dokumente, Diktat and Bilder verstehen are
 * stored in [AppSettings.places].
 */
object Places {
    /** The three quick choices at the top. */
    enum class Profile(val label: String) {
        ALL_LOCAL(trLater("Alles lokal")),
        MIXED(trLater("Gemischt (empfohlen)")),
        ALL_API(trLater("Alles über API")),
    }

    fun parse(text: String): Map<Area, Place> = text.split(';', ',').mapNotNull { part ->
        val (a, p) = part.split('=').map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        val area = Area.entries.firstOrNull { it.name == a } ?: return@mapNotNull null
        val place = Place.entries.firstOrNull { it.name == p } ?: return@mapNotNull null
        area to place
    }.toMap()

    fun format(map: Map<Area, Place>): String = Area.entries.mapNotNull { a -> map[a]?.let { "${a.name}=${it.name}" } }.joinToString(";")

    /** The place chosen for [area] (fixed areas: their place; the Web: none – it only needs Internet). */
    fun chosen(c: AppContainer, area: Area): Place? = chosen(c.settings.current, c.transcription.settings.value.mode, area)

    fun chosen(s: AppSettings, transcriptionMode: String, area: Area): Place? {
        val stored = parse(s.places)
        return when (area) {
            Area.LORA -> when (s.loraPlace) { "LOCAL" -> Place.LOCAL; "SERVER" -> Place.API; else -> Place.MIXED }
            Area.CHAT -> stored[Area.CHAT] ?: if (Privacy.isLocal(s.defaultModel)) Place.LOCAL else Place.API
            Area.DOCS -> stored[Area.DOCS] ?: chosen(s, transcriptionMode, Area.CHAT)
            Area.TRANSCRIPTION -> if (transcriptionMode == "SERVER") Place.API else if (stored[Area.TRANSCRIPTION] == Place.LOCAL) Place.LOCAL else Place.MIXED
            Area.DICTATION -> stored[Area.DICTATION] ?: Place.MIXED
            Area.SEARCH -> if (!s.semanticLocal) Place.API else if (stored[Area.SEARCH] == Place.LOCAL) Place.LOCAL else Place.MIXED
            Area.SPEECH -> if (s.ttsEngine == "SERVER") Place.API else Place.LOCAL
            Area.VISION -> stored[Area.VISION] ?: Place.MIXED
            Area.MAIL, Area.NAMES, Area.COMMANDS -> Place.LOCAL
            Area.IMAGES -> Place.API
            Area.WEB -> null
        }
    }

    /** An API for [area] is possible at all: speech, embeddings and transcription need the key of the Uni-Server. */
    fun hasKeyFor(s: AppSettings, area: Area): Boolean = when (area) {
        Area.TRANSCRIPTION, Area.DICTATION, Area.SEARCH, Area.SPEECH -> s.hasApiKey
        else -> s.hasAnyApiKey
    }

    /** What really applies: without a key nothing can go over an API, so a choosable area works on this device. */
    fun effective(c: AppContainer, area: Area): Place? = effective(c.settings.current, c.transcription.settings.value.mode, area)

    fun effective(s: AppSettings, transcriptionMode: String, area: Area): Place? {
        val p = chosen(s, transcriptionMode, area) ?: return null
        if (p != Place.LOCAL && area.choosable && !hasKeyFor(s, area)) return Place.LOCAL
        return p
    }

    /** Lora's mode for the router (AP-71), after [effective]. */
    fun loraMode(c: AppContainer): io.github.thepytales.llmhub.agent.ModelRouter.Mode = when (effective(c, Area.LORA)) {
        Place.LOCAL -> io.github.thepytales.llmhub.agent.ModelRouter.Mode.LOCAL
        Place.API -> io.github.thepytales.llmhub.agent.ModelRouter.Mode.SERVER
        else -> io.github.thepytales.llmhub.agent.ModelRouter.Mode.MIXED
    }

    /** Sets [area] to [place], in its own setting where it has one. */
    fun set(c: AppContainer, area: Area, place: Place) {
        if (place !in area.places) return
        val voice = c.localVoice.installed()
        c.settings.update { s ->
            val map = parse(s.places).toMutableMap()
            var next = s
            when (area) {
                Area.LORA -> next = s.copy(loraPlace = when (place) { Place.LOCAL -> "LOCAL"; Place.API -> "SERVER"; Place.MIXED -> "MIXED" })
                Area.SPEECH -> next = s.copy(ttsEngine = if (place == Place.API) "SERVER" else if (voice) "LOCAL" else if (s.ttsEngine == "SERVER") "DEVICE" else s.ttsEngine)
                Area.SEARCH -> { next = s.copy(semanticLocal = place != Place.API); map[area] = place }
                Area.CHAT -> {
                    map[area] = place
                    // Back to the server: a local default model gives way to the default of the Uni-Server.
                    if (place == Place.API && Privacy.isLocal(s.defaultModel)) next = s.copy(defaultModel = ModelCatalog.DEFAULT_CHAT)
                }
                else -> map[area] = place
            }
            next.copy(places = format(map))
        }
        if (area == Area.TRANSCRIPTION) c.transcription.updateSettings { it.copy(mode = if (place == Place.API) "SERVER" else "LOCAL") }
    }

    /** The place an area gets from a quick choice (the first one it allows). */
    fun placeIn(profile: Profile, area: Area): Place? {
        val wanted = when (profile) {
            Profile.ALL_LOCAL -> listOf(Place.LOCAL)
            Profile.ALL_API -> listOf(Place.API, Place.MIXED, Place.LOCAL)
            // Gemischt (empfohlen): what the app did before AP-112.
            Profile.MIXED -> when (area) {
                Area.CHAT, Area.DOCS, Area.SPEECH -> listOf(Place.API, Place.LOCAL)
                else -> listOf(Place.MIXED, Place.LOCAL, Place.API)
            }
        }
        return wanted.firstOrNull { it in area.places }
    }

    /** Applies a quick choice to every area that has a choice. */
    fun apply(c: AppContainer, profile: Profile) {
        Area.entries.filter { it.choosable }.forEach { a -> placeIn(profile, a)?.let { set(c, a, it) } }
    }

    /** The quick choice the current places match (as they apply: without a key everything is local), null for an own mix. */
    fun profile(c: AppContainer): Profile? =
        Profile.entries.firstOrNull { p -> Area.entries.filter { it.choosable }.all { a -> effective(c, a) == placeIn(p, a) } }

    /** „Alles auf diesem Gerät“: every area that has a choice is local. */
    fun allLocal(c: AppContainer) = profile(c) == Profile.ALL_LOCAL

    // ── What runs on this device ───────────────────────────────────────────────────────────────────────────────

    /** The labels of a function (AP-112): läuft auf diesem Gerät · braucht Internet · nur über API · nicht auf diesem Gerät. */
    enum class Kind(val label: String) {
        READY(trLater("läuft auf diesem Gerät")),
        SETUP(trLater("noch nicht geladen")),
        NOT_HERE(trLater("nicht auf diesem Gerät")),
        INTERNET(trLater("braucht Internet")),
        API_ONLY(trLater("nur über API")),
        API(trLater("über API")),
    }

    /** One line of the overview: the label, why, and which step of the setup loads what is missing. */
    data class Status(val kind: Kind, val reason: String, val step: SetupStep? = null)

    /** Steps of the setup „Alles lokal“ (AP-112), in this order. */
    enum class SetupStep(val title: String) {
        DEVICE(trLater("Gerät messen")),
        PURPOSE(trLater("Wofür vor allem?")),
        LANGUAGE(trLater("Sprachmodell")),
        HELPERS(trLater("Befehle und Namensschutz")),
        SEARCH(trLater("Suche nach Bedeutung")),
        SPEECH_IN(trLater("Spracherkennung")),
        VOICE(trLater("Stimme")),
        VISION(trLater("Bilder verstehen")),
        DONE(trLater("Übersicht")),
    }

    private fun gb(v: Double) = String.format(Locale.GERMANY, "%.0f", v)

    /** The smallest memory a language model needs here: the smallest model of the catalog. */
    private fun minRamGb(): Double = LocalCatalog.models.minOf { it.needGb } / 0.6

    /** Why no language model runs on [hw], with the step it would need; null if one fits. */
    fun languageMissing(hw: Hardware?): String? {
        hw ?: return null
        if (LocalCatalog.pick(Purpose.ALLROUND, hw) != null) return null
        return tr("Zu wenig Arbeitsspeicher ({0} GB) – braucht mindestens {1} GB.", gb(hw.ramGb), gb(Math.ceil(minRamGb())))
    }

    /** Seconds for one image with the model that understands images here (installed or recommended); null if none. */
    fun secondsPerImage(c: AppContainer, hw: Hardware): Double? {
        val installed = LocalVision.model(c)?.let { LocalCatalog.get(it) }
        val m = installed ?: LocalCatalog.pick(Purpose.ALLROUND, hw)?.model?.takeIf { it.projector != null }
            ?: LocalCatalog.models.filter { it.projector != null && LocalCatalog.fit(it, hw) != io.github.thepytales.llmhub.local.Fit.TOO_BIG }.minByOrNull { it.needGb }
            ?: return null
        return LocalVision.secondsPerImage(m, hw, DeviceBench.forecast(c, m, hw))
    }

    /** Above this, understanding images locally is not worth it on this device (AP-110 aimed at 15 s on an Office-PC). */
    const val VISION_TOO_SLOW_S = 60.0

    fun status(c: AppContainer, area: Area): Status {
        val s = c.settings.current
        val hw = c.localLlm.hardware.value
        val chosen = chosen(c, area)
        val place = effective(c, area)
        val noKey = chosen != null && chosen != Place.LOCAL && place == Place.LOCAL
        val language = c.localLlm.generalModels().isNotEmpty()
        fun local(ready: Boolean, readyText: String, missing: String, step: SetupStep, notHere: String? = null): Status {
            val prefix = if (noKey) tr("Kein API-Schlüssel – darum auf diesem Gerät. ") else ""
            return when {
                notHere != null && !ready -> Status(Kind.NOT_HERE, prefix + notHere)
                ready -> Status(Kind.READY, prefix + readyText)
                else -> Status(Kind.SETUP, prefix + missing, step)
            }
        }
        fun api(text: String): Status =
            if (hasKeyFor(s, area)) Status(Kind.API, text) else Status(Kind.API, tr("Gewählt: API – dafür fehlt ein API-Schlüssel (Einstellungen › API-Zugang)."))
        val modelName = c.localLlm.generalModels().maxByOrNull { it.quality }?.let { tr(it.name) }.orEmpty()
        return when (area) {
            Area.WEB -> Status(Kind.INTERNET, tr("Websuche, Literatur, Nextcloud und Updates brauchen Internet, aber keinen API-Schlüssel."))
            Area.IMAGES -> Status(
                Kind.API_ONLY,
                if (s.hasApiKey) tr("Nur über den Uni-Server – die Beschreibung verlässt das Gerät.")
                else tr("Nur über API – braucht einen API-Schlüssel. Alles andere geht ohne."),
            )
            Area.NAMES -> local(
                c.piiModel.installed(), tr("Lora Namensschutz und die Regeln ersetzen Namen auf diesem Gerät."),
                tr("Die Regeln laufen schon; Lora Namensschutz findet mehr Namen."), SetupStep.HELPERS,
            )
            Area.COMMANDS -> local(
                c.commandModel.installed(), tr("Lora Befehle erkennt Befehle in Millisekunden."),
                tr("Lora Befehle laden – bis dahin erkennt Lora Befehle an Wörtern."), SetupStep.HELPERS,
            )
            Area.MAIL -> local(language, tr("{0} liest das Postfach.", modelName), tr("Braucht ein Sprachmodell auf diesem Gerät."), SetupStep.LANGUAGE, languageMissing(hw))
            Area.LORA, Area.CHAT, Area.DOCS -> when (place) {
                Place.LOCAL -> local(
                    language,
                    if (area == Area.LORA) tr("{0} antwortet; Werkzeugketten sind schlichter als mit dem Server.", modelName) else tr("{0} antwortet.", modelName),
                    tr("Braucht ein Sprachmodell auf diesem Gerät."), SetupStep.LANGUAGE, languageMissing(hw),
                )
                Place.MIXED -> if (language) Status(Kind.READY, tr("Befehle und kurze Aufgaben hier, Anspruchsvolles über den Server."))
                else Status(Kind.API, tr("Befehle hier, alles andere über den Server – mit einem Sprachmodell auch kurze Aufgaben hier."))
                else -> api(tr("Über den Uni-Server bzw. den gewählten Anbieter."))
            }
            Area.TRANSCRIPTION, Area.DICTATION -> {
                val model = c.transcription.localModel() != null
                when (place) {
                    Place.API -> api(tr("Whisper auf dem Uni-Server."))
                    Place.MIXED -> if (model) Status(Kind.READY, tr("Auf diesem Gerät; ohne Erkennungsmodell über den Uni-Server."))
                    else Status(Kind.API, tr("Bis ein Erkennungsmodell geladen ist, über den Uni-Server."), SetupStep.SPEECH_IN)
                    else -> local(model, tr("Erkennungsmodell auf diesem Gerät."), tr("Braucht ein Erkennungsmodell (ab 75 MB)."), SetupStep.SPEECH_IN)
                }
            }
            Area.SEARCH -> when (place) {
                Place.API -> api(tr("Einbettungen des Uni-Servers."))
                Place.MIXED -> if (c.localEmbedding.installed()) Status(Kind.READY, tr("multilingual-e5-small auf diesem Gerät."))
                else Status(Kind.API, tr("Bis das Suchmodell geladen ist, über den Uni-Server."), SetupStep.SEARCH)
                else -> local(c.localEmbedding.installed(), tr("multilingual-e5-small auf diesem Gerät."), tr("Bis das Suchmodell (123 MB) geladen ist, nur Wortsuche."), SetupStep.SEARCH)
            }
            Area.SPEECH -> when (place) {
                Place.API -> api(tr("KI-Stimme des Uni-Servers."))
                else -> if (c.localVoice.installed()) local(true, tr("Natürliche Stimme (Piper Thorsten) auf diesem Gerät."), "", SetupStep.VOICE)
                else Status(Kind.READY, tr("Stimme des Systems; die natürliche Stimme (64 MB) klingt besser."), SetupStep.VOICE)
            }
            Area.VISION -> {
                val sec = hw?.let { secondsPerImage(c, it) }
                val slow = sec != null && sec > VISION_TOO_SLOW_S
                val notHere = when {
                    hw != null && sec == null -> tr("Kein Modell mit Bildteil passt in den Speicher – braucht mindestens {0} GB.", gb(Math.ceil(minRamGb())))
                    slow -> tr("Zu langsam: etwa {0} s je Bild – braucht eine Grafikkarte ab 6 GB oder einen Apple-Chip.", sec!!.toInt())
                    else -> null
                }
                val time = sec?.let { tr(" Etwa {0} s je Bild.", maxOf(1, it.toInt())) }.orEmpty()
                when (place) {
                    Place.API -> api(tr("Bildmodell des Uni-Servers."))
                    Place.MIXED -> if (LocalVision.model(c) != null && !slow) Status(Kind.READY, tr("Auf diesem Gerät, sonst über den Uni-Server.") + time)
                    else Status(Kind.API, tr("Über den Uni-Server, bis ein Bildteil auf diesem Gerät geladen ist.") + time, SetupStep.VISION.takeIf { !slow })
                    else -> local(LocalVision.model(c) != null, tr("Bildteil auf diesem Gerät.") + time, tr("Braucht den Bildteil zum Sprachmodell.") + time, SetupStep.VISION, notHere)
                }
            }
        }
    }

    /** How many areas with a choice work on this device now. */
    fun localCount(c: AppContainer): Pair<Int, Int> {
        val areas = Area.entries.filter { it != Area.WEB && it != Area.IMAGES }
        return areas.count { status(c, it).kind == Kind.READY && effective(c, it) != Place.API } to areas.size
    }

    // ── The gate ─────────────────────────────────────────────────────────────────────────────────────────────

    /** The message when [area] must stay here but nothing here can do it. */
    fun missingNote(c: AppContainer, area: Area): String {
        val why = if (!hasKeyFor(c.settings.current, area)) tr("Ohne API-Schlüssel läuft „{0}“ auf diesem Gerät", tr(area.title))
        else tr("„{0}“ läuft nur auf diesem Gerät", tr(area.title))
        val what = when (area) {
            Area.TRANSCRIPTION, Area.DICTATION -> tr("dafür fehlt noch ein Erkennungsmodell")
            Area.SEARCH -> tr("dafür fehlt noch das Suchmodell")
            Area.SPEECH -> tr("dafür fehlt noch die Stimme")
            Area.VISION -> tr("dafür fehlt noch ein Sprachmodell mit Bildteil")
            else -> tr("dafür fehlt noch ein Sprachmodell")
        }
        return tr("{0} – {1}. Einstellungen › Wo Lora arbeitet › Einrichtung lädt es (oder dort einen API-Schlüssel eintragen).", why, what)
    }

    /**
     * The model a chat request of [area] goes to: [model] where the area may use it, else the local model that fits
     * (with images: one that sees them). Throws [OnDeviceOnly] when the request must stay here and nothing here runs it.
     */
    fun route(c: AppContainer, area: Area, model: String, images: Boolean): String {
        if (Privacy.isLocal(model)) return model
        if (effective(c, area) != Place.LOCAL) return model
        // With pictures only a model that sees them – a text model would answer without having looked.
        if (images) return LocalVision.model(c) ?: throw OnDeviceOnly(missingNote(c, Area.VISION))
        return c.localLlm.preferredModel() ?: throw OnDeviceOnly(missingNote(c, area))
    }

    /** Other requests (speech, embeddings, recognition) of [area] may go to the server. */
    fun serverAllowed(c: AppContainer, area: Area): Boolean = effective(c, area) != Place.LOCAL

    /** The model a new chat starts with: [wanted], or the local one where the chat works on this device. */
    fun chatModel(c: AppContainer, wanted: String): String {
        if (Privacy.isLocal(wanted) || effective(c, Area.CHAT) != Place.LOCAL) return wanted
        return c.localLlm.preferredModel() ?: wanted
    }

    /**
     * Speech of a short recording as text (chat, Lora, Diktat): on this device first, the Uni-Server only where
     * Spracheingabe may use it ([deviceOnly]: a project „Nur auf diesem Gerät“). Throws with a plain message otherwise.
     */
    suspend fun recognize(c: AppContainer, file: java.io.File, deviceOnly: Boolean = false): Pair<String, Boolean> {
        val place = if (deviceOnly) Place.LOCAL else effective(c, Area.DICTATION)
        if (place != Place.API) c.transcription.recognizeShort(file)?.let { return it to true }
        if (place == Place.LOCAL) {
            throw OnDeviceOnly(
                if (deviceOnly) tr("In diesem Projekt bleibt Sprache auf dem Gerät – dafür unter Einstellungen › Transkription ein Erkennungsmodell laden.")
                else missingNote(c, Area.DICTATION),
            )
        }
        return kotlinx.coroutines.withContext(PlaceScope(Area.DICTATION)) { c.llm.transcribe(c.settings.current.sttModel, file) } to false
    }

    /** Spracheingabe is possible here at all (a recognition model, or the Uni-Server). */
    fun canRecognize(c: AppContainer): Boolean =
        c.transcription.localModel() != null || (effective(c, Area.DICTATION) != Place.LOCAL && c.settings.current.hasApiKey)
}
