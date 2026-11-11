package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.tr

/**
 * Lokale Modelle: Tempo, Abbruch und Hitze (AP-116). What a model on this device may do in one answer, after the class
 * of the device: on a phone and on a PC without a graphics card every token is computed on the processor, and each
 * tool step reads the whole history again – so few steps, no thinking, short mails and a time limit with a clear hint.
 *
 * The numbers are a first setting from the code (1.0.67 on the OnePlus 15: 9 steps, 10 minutes, no answer); they are to
 * be measured on the devices (docs/ARBEITSPLAN.md, AP-116) and then adjusted here.
 */
data class LocalLimits(
    val device: DeviceClass,
    /** Tool rounds before the model answers with what it has (no automatic continuation). */
    val maxSteps: Int,
    /** Thinking always off (phone), whatever the settings say. */
    val forceThinkingOff: Boolean,
    /** „Modellstandard“ becomes „Aus“ (PC without a graphics card); an explicit choice in the settings stays. */
    val autoThinkingOff: Boolean,
    /** Time for one whole answer (all steps); then the hint [tooSlow]. */
    val answerMs: Long,
    /** Mails listed when the model does not ask for a number, and the most it gets. */
    val mailList: Int,
    val mailListMax: Int,
    /** Characters of the beginning of a mail in a list. */
    val previewChars: Int,
    /** Characters of a mail read (head and beginning), and with full=true. */
    val mailChars: Int,
    val fullMailChars: Int,
) {
    /** The thinking level a request to the model on this device gets for the [wanted] one (null: the settings'). */
    fun thinking(wanted: ThinkingLevel): ThinkingLevel = when {
        forceThinkingOff -> ThinkingLevel.OFF
        autoThinkingOff && wanted == ThinkingLevel.AUTO -> ThinkingLevel.OFF
        else -> wanted
    }

    companion object {
        val PHONE = LocalLimits(
            DeviceClass.PHONE, maxSteps = 3, forceThinkingOff = true, autoThinkingOff = true, answerMs = 4 * 60_000L,
            mailList = 15, mailListMax = 30, previewChars = 100, mailChars = 1_200, fullMailChars = 6_000,
        )
        val PC_CPU = LocalLimits(
            DeviceClass.PC_CPU, maxSteps = 6, forceThinkingOff = false, autoThinkingOff = true, answerMs = 8 * 60_000L,
            mailList = 25, mailListMax = 100, previewChars = 140, mailChars = 2_000, fullMailChars = 12_000,
        )
        val PC_FAST = LocalLimits(
            DeviceClass.PC_FAST, maxSteps = 12, forceThinkingOff = false, autoThinkingOff = false, answerMs = 15 * 60_000L,
            mailList = 40, mailListMax = 200, previewChars = 160, mailChars = 2_500, fullMailChars = 20_000,
        )

        /** Reading speed (tokens/s, prompt processing) from which a PC counts as fast – a graphics card or an Apple chip reads ≥ 300. */
        const val FAST_READ_TOK_S = 300.0
        /** Below this a PC with a graphics card still gets the limits of one without (old card, model on the processor). */
        const val SLOW_READ_TOK_S = 120.0

        /**
         * The class of this device for [model]: a phone; a PC whose model runs on the graphics card or an Apple chip
         * (fast); else a PC on the processor. A measurement or forecast of the reading speed (AP-105, [forecast])
         * decides where it is known: slow reading makes a fast PC a processor one, fast reading the other way round.
         */
        fun of(hw: Hardware?, model: LocalModel? = null, forecast: SpeedForecast.Forecast? = null): LocalLimits {
            if (hw == null) return PC_CPU
            if (hw.phone) return PHONE
            val gpu = hw.chip.isNotBlank() || (model?.let { hw.vramGb >= 4 && it.needGb <= hw.vramGb * 0.92 } ?: (hw.vramGb >= 8))
            val read = forecast?.readTokS?.takeIf { it.isFinite() && it > 0 }
            return when {
                read != null && read >= FAST_READ_TOK_S -> PC_FAST
                read != null && read < SLOW_READ_TOK_S -> PC_CPU
                gpu -> PC_FAST
                else -> PC_CPU
            }
        }

        /** The hint when an answer takes longer than [LocalLimits.answerMs]; in the Postfach no server model may help. */
        fun tooSlow(mailbox: Boolean): String = if (mailbox) {
            tr("Das dauert auf diesem Gerät zu lange – Lora hat angehalten. Eine kürzere Frage stellen (z. B. nur ungelesene Mails von heute) oder das Postfach am PC fragen?")
        } else {
            tr("Das dauert auf diesem Gerät zu lange – Lora hat angehalten. Ein Server-Modell wählen oder eine kürzere Frage stellen?")
        }

        /** The note in the last request when the steps are used up: answer now with what is there. */
        const val LAST_STEP_NOTE = "(Hinweis der App: Die Werkzeugschritte für diese Antwort sind aufgebraucht. Antworte jetzt direkt " +
            "und kurz mit dem, was du bisher herausgefunden hast – ohne weitere Werkzeuge. Fehlt etwas, sag, was noch offen ist.)"

        /**
         * Threads for llama-server: on a PC half the cores (it stays usable), 2 to 8; on a phone only the big cores and
         * at most half of all, 2 to 4 – full load on every core made the OnePlus 15 hot in 1.0.67.
         */
        fun threads(hw: Hardware): Int = if (hw.phone) {
            val big = hw.bigCores.takeIf { it > 0 } ?: hw.cores
            minOf(big, hw.cores / 2).coerceIn(2, 4)
        } else (hw.cores / 2).coerceIn(2, 8)
    }
}

/** How fast a device computes a model on this device (AP-116). */
enum class DeviceClass { PHONE, PC_CPU, PC_FAST }

/**
 * Hitzeschutz (AP-116, Android): the state of the system's thermal management – [OK]; [WARM]: no new work, first cool
 * down; [HOT]: stop the running work at once, before the system throttles hard or the phone gets hot in the hand.
 */
enum class Heat {
    OK, WARM, HOT;

    companion object {
        /** PowerManager.THERMAL_STATUS_MODERATE / _SEVERE. */
        const val STATUS_MODERATE = 2
        const val STATUS_SEVERE = 3

        /**
         * From PowerManager: [status] (getCurrentThermalStatus, 0 none … 6 shutdown, -1 unknown) and [headroom]
         * (getThermalHeadroom: 1.0 = severe throttling within the forecast, NaN if unknown).
         */
        fun of(status: Int, headroom: Float): Heat = when {
            status >= STATUS_SEVERE || (!headroom.isNaN() && headroom >= 1.0f) -> HOT
            status >= STATUS_MODERATE || (!headroom.isNaN() && headroom >= 0.85f) -> WARM
            else -> OK
        }

        /** Hints for the chat. */
        fun waitNote(): String = tr("Das Gerät ist warm – Lora macht eine Pause zum Abkühlen")
        fun stopNote(): String = tr("Das Gerät wird zu warm – Lora hat angehalten, damit es nicht überhitzt. In ein paar Minuten noch einmal versuchen oder eine kürzere Frage stellen.")
    }
}

/** A model on this device stopped for a reason the user should read as it is (too slow, too warm) – not „Netz weg“. */
class OnDeviceStop(message: String) : Exception(message)
