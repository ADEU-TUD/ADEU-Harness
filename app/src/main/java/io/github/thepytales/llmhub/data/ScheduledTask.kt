package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import io.github.thepytales.llmhub.ui.I18n
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater

@Serializable
enum class Repeat { DAYS, INTERVAL }

/** A prompt the agent runs on a schedule, e.g. a news summary every morning. */
@Serializable
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val model: String,
    val enabled: Boolean = true,
    val repeat: Repeat = Repeat.DAYS,
    val hour: Int = 7,
    val minute: Int = 0,
    /** ISO days of week (1 = Monday … 7 = Sunday) for [Repeat.DAYS]. */
    val days: List<Int> = (1..7).toList(),
    val intervalHours: Int = 6,
    /** Web search, news search and reading web pages. */
    val webTools: Boolean = true,
    val documentTools: Boolean = false,
    val github: Boolean = false,
    /** Single repository of tasks from before several repositories; see [linkedRepos]. */
    val repo: String? = null,
    /** Repositories the task works in (owner/name). */
    val repos: List<String> = emptyList(),
    /** Allow GitHub write actions without confirmation (nobody can confirm in the background). */
    val allowWrites: Boolean = false,
    val envId: String? = null,
    val systemPrompt: String = "",
    val notify: Boolean = true,
    /** Folder that collects the result chats. */
    val folderId: String? = null,
    val lastRunAt: Long? = null,
    val lastStatus: String? = null,
    val lastChatId: String? = null,
    val nextRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** AP-69 d: a sort order of Lora („jeden Montag die letzte Woche einordnen“), run by the model on this device instead of a chat. */
    val mailSort: io.github.thepytales.llmhub.agent.MailSortSpec? = null,
) {
    val linkedRepos: List<String> get() = repos.ifEmpty { listOfNotNull(repo) }

    fun withRepos(list: List<String>) = copy(repos = list.distinctBy { it.lowercase() }, repo = list.firstOrNull())

    /** Next run strictly after [after] (epoch millis), or null if no day is selected. */
    fun nextRun(after: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? = when (repeat) {
        Repeat.INTERVAL -> {
            val step = intervalHours.coerceAtLeast(1) * 3_600_000L
            val base = (lastRunAt ?: after) + step
            if (base > after) base else after + step
        }
        Repeat.DAYS -> {
            val today: LocalDate = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
            (0..7).asSequence()
                .map { today.plusDays(it.toLong()) }
                .filter { it.dayOfWeek.value in days }
                .map { it.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli() }
                .firstOrNull { it > after }
        }
    }

    fun scheduleLabel(): String = when (repeat) {
        Repeat.INTERVAL -> if (intervalHours == 1) tr("Stündlich") else tr("Alle {0} Stunden", intervalHours)
        Repeat.DAYS -> {
            val time = "%02d:%02d".format(hour, minute)
            val sorted = days.sorted()
            val label = when {
                sorted == (1..7).toList() -> tr("Täglich")
                sorted == (1..5).toList() -> tr("Mo–Fr")
                sorted == listOf(6, 7) -> tr("Am Wochenende")
                sorted.isEmpty() -> tr("Nie")
                else -> {
                    val locale = if (I18n.language == "en") Locale.ENGLISH else Locale.GERMAN
                    sorted.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
                }
            }
            tr("{0} um {1}", label, time)
        }
    }

    companion object {
        /** [lastStatus] of a run without errors; the status is stored in German and shown with [statusText]. */
        const val SUCCESS = "Erfolgreich"
        const val CANCELLED = "Abgebrochen"
        const val ERROR_PREFIX = "Fehler: "

        /** The stored [lastStatus] in the language of the interface. */
        fun statusText(status: String): String = when {
            status == SUCCESS -> tr("Erfolgreich")
            status == CANCELLED -> tr("Abgebrochen")
            status.startsWith(ERROR_PREFIX) -> tr("Fehler: {0}", status.removePrefix(ERROR_PREFIX))
            else -> tr(status)
        }
    }
}

object TaskPresets {
    fun news(model: String) = ScheduledTask(
        name = trLater("Morgen-News"),
        model = model,
        prompt = "Recherchiere mit news_search die wichtigsten Nachrichten der letzten 24 Stunden zum Thema " +
            "„Künstliche Intelligenz“. Fasse die 5 relevantesten Meldungen jeweils in 2–3 Sätzen zusammen, " +
            "nenne Quelle und Link und schließe mit einem kurzen Fazit.",
    )
}
