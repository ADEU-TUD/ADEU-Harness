package io.github.thepytales.llmhub.transcribe

import io.github.thepytales.llmhub.logFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * Transcripts as the speech engine writes them (transcript.json, version 3) and the state it reports. Unknown fields
 * are kept when a transcript is saved again, so older and newer versions can read each other's files.
 */

internal val SpeechJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

/**
 * One word with its measured time, for highlighting while playing and exact time stamps. [quality]: 'm' measured,
 * 's' measured but spelled differently by the measuring model (e.g. "45" / "fünfundvierzig"), 'i' not measured and
 * placed between its measured neighbours (see de.lokal.whisper.WordAlign).
 */
data class Word(val start: Double, val end: Double, val text: String, val quality: Char = 'm') {
    val measured get() = quality != 'i'
}

data class Segment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String,
    /** Index of the speaker, null without speaker detection. */
    val speaker: Int? = null,
    val words: List<Word> = emptyList(),
    /** Note on this passage (Korrektur, Kodierhinweis …), not part of the spoken text. */
    val comment: String? = null,
)

/** Chapter found by the language model: where a topic starts. */
data class Chapter(val time: Double, val title: String)

/** How far a transcript has been checked. */
enum class TranscriptStatus(val key: String, val label: String) {
    RAW("", "Rohfassung"),
    CORRECTED("corrected", "Korrigiert"),
    CHECKED("checked", "Geprüft"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key.orEmpty() } ?: RAW
    }
}

/** Where the word times come from (transcript.json "word_timing"). */
data class WordTiming(val source: String, val measured: Int, val respelled: Int, val interpolated: Int)

/** Marker set while recording ("Wichtig", "Frage 2" …). */
data class Mark(val time: Double, val label: String)

data class Transcript(
    val id: String,
    val title: String,
    val created: String,
    val duration: Double,
    val language: String?,
    val modelName: String,
    /** "phone" or "pc" (device on which it was transcribed); "cloud" models say so in [modelName]. */
    val device: String?,
    val speakers: Map<Int, String>,
    val segments: List<Segment>,
    /** File name of the audio next to transcript.json, null if none was kept. */
    val audio: String?,
    /** Project (ADEU folder id), null for none. */
    val folder: String?,
    val marks: List<Mark>,
    val notice: String?,
    /** The file as read, to keep fields this version does not know. */
    val raw: JsonObject,
    val status: TranscriptStatus = TranscriptStatus.RAW,
    val chapters: List<Chapter> = emptyList(),
    /** Summary written by the language model, null if none. */
    val summary: String? = null,
) {
    val diarized get() = speakers.isNotEmpty() && segments.any { it.speaker != null }
    fun speakerName(i: Int?): String = if (i == null) "" else speakers[i] ?: "Sprecher ${i + 1}"
    val wordCount get() = segments.sumOf { s -> s.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } }
    val plainText get() = segments.joinToString("\n") { it.text }
    /** Share of the words that have a measured time (0..1). */
    val measuredShare get(): Double {
        val total = segments.sumOf { s -> s.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } }
        return if (total == 0) 0.0 else segments.sumOf { s -> s.words.count { it.measured } }.toDouble() / total
    }
    val wordTiming get() = raw.obj("word_timing")?.let { o ->
        WordTiming(o.str("source").orEmpty(), o.int("measured") ?: 0, o.int("respelled") ?: 0, o.int("interpolated") ?: 0)
    }
    /** The version as first transcribed, kept on the first edit ("Änderungen verwerfen"). */
    val hasOriginal get() = raw["original"] is JsonObject

    fun toJson(): JsonObject = buildJsonObject {
        raw.forEach { (k, v) -> if (k !in OWN_KEYS) put(k, v) }
        put("id", id)
        put("title", title)
        put("created", created)
        put("duration", duration)
        put("language", language?.let { JsonPrimitive(it) } ?: JsonNull)
        put("model_name", modelName)
        device?.let { put("device", it) }
        put("diarization", speakers.isNotEmpty())
        put("speakers", buildJsonObject { speakers.toSortedMap().forEach { (k, v) -> put(k.toString(), v) } })
        put("segments", buildJsonArray {
            segments.forEachIndexed { i, s ->
                add(buildJsonObject {
                    put("id", i)
                    put("start", s.start)
                    put("end", s.end)
                    put("text", s.text)
                    s.speaker?.let { put("speaker", it) }
                    if (s.words.isNotEmpty()) put("words", wordsJson(s.words))
                    s.comment?.takeIf { it.isNotBlank() }?.let { put("comment", it) }
                })
            }
        })
        put("audio", audio?.let { JsonPrimitive(it) } ?: JsonNull)
        put("folder", folder?.let { JsonPrimitive(it) } ?: JsonNull)
        if (marks.isNotEmpty()) put("marks", buildJsonArray {
            marks.forEach { m -> add(buildJsonObject { put("t", m.time); put("label", m.label) }) }
        })
        notice?.let { put("notice", it) }
        if (status != TranscriptStatus.RAW) put("status", status.key)
        if (chapters.isNotEmpty()) put("chapters", buildJsonArray {
            chapters.forEach { ch -> add(buildJsonObject { put("t", ch.time); put("title", ch.title) }) }
        })
        summary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
        put("version", 3)
    }

    /** Keeps the current segments and speakers as the original, once (before the first edit). */
    fun withOriginal(): Transcript = if (hasOriginal) this else copy(
        raw = JsonObject(raw + ("original" to buildJsonObject {
            val j = toJson()
            j["segments"]?.let { put("segments", it) }
            j["speakers"]?.let { put("speakers", it) }
            put("saved", JsonPrimitive(created))
        })),
    )

    /** The original version (segments and speakers) with everything else as it is now, null without one. */
    fun original(): Transcript? {
        val o = raw.obj("original") ?: return null
        val j = JsonObject(toJson() + ("segments" to (o["segments"] ?: return null)) + ("speakers" to (o["speakers"] ?: buildJsonObject { })) +
            ("diarization" to JsonPrimitive((o.obj("speakers")?.isNotEmpty()) == true)))
        return parse(j.toString(), id)
    }

    companion object {
        private val OWN_KEYS = setOf(
            "id", "title", "created", "duration", "language", "model_name", "device", "diarization", "speakers",
            "segments", "audio", "folder", "marks", "notice", "version", "status", "chapters", "summary",
        )

        internal fun wordsJson(words: List<Word>) = buildJsonArray {
            words.forEach { w ->
                add(buildJsonArray {
                    add(JsonPrimitive(Math.round(w.start * 100) / 100.0)); add(JsonPrimitive(Math.round(w.end * 100) / 100.0)); add(JsonPrimitive(w.text))
                    if (w.quality != 'm') add(JsonPrimitive(w.quality.toString()))
                })
            }
        }

        fun parse(text: String, dirName: String): Transcript? = runCatching {
            val o = SpeechJson.parseToJsonElement(text).jsonObject
            val speakers = o.obj("speakers")?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to ((v as? JsonPrimitive)?.contentOrNull ?: "Sprecher ${it + 1}") } }?.toMap()
                .orEmpty().takeIf { o.bool("diarization") == true }.orEmpty()
            val segments = o.arr("segments").orEmpty().mapIndexedNotNull { i, el ->
                val s = el as? JsonObject ?: return@mapIndexedNotNull null
                Segment(
                    id = i,
                    start = s.num("start") ?: 0.0,
                    end = s.num("end") ?: 0.0,
                    text = s.str("text").orEmpty(),
                    speaker = if (speakers.isEmpty()) null else s.int("speaker"),
                    words = s.arr("words").orEmpty().mapNotNull { w ->
                        val a = w as? JsonArray ?: return@mapNotNull null
                        if (a.size < 3) null else Word(
                            (a[0] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                            (a[1] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                            (a[2] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                            (a.getOrNull(3) as? JsonPrimitive)?.contentOrNull?.firstOrNull() ?: 'm',
                        )
                    },
                    comment = s.str("comment")?.takeIf { it.isNotBlank() },
                )
            }
            Transcript(
                id = o.str("id") ?: dirName,
                title = o.str("title").orEmpty().ifBlank { "Transkript" },
                created = o.str("created").orEmpty(),
                duration = o.num("duration") ?: segments.lastOrNull()?.end ?: 0.0,
                language = o.str("language"),
                modelName = o.str("model_name").orEmpty(),
                device = o.str("device"),
                speakers = speakers,
                segments = segments,
                audio = o.str("audio")?.takeIf { it.isNotBlank() },
                folder = o.str("folder")?.takeIf { it.isNotBlank() },
                marks = o.arr("marks").orEmpty().mapNotNull { m ->
                    val x = m as? JsonObject ?: return@mapNotNull null
                    Mark(x.num("t") ?: x.num("time") ?: 0.0, x.str("label").orEmpty())
                },
                notice = o.str("notice"),
                raw = o,
                status = TranscriptStatus.of(o.str("status")),
                chapters = o.arr("chapters").orEmpty().mapNotNull { c ->
                    val x = c as? JsonObject ?: return@mapNotNull null
                    Chapter(x.num("t") ?: 0.0, x.str("title").orEmpty()).takeIf { it.title.isNotBlank() }
                },
                summary = o.str("summary")?.takeIf { it.isNotBlank() },
            )
        }.logFailure("Transkript lesen").getOrNull()
    }
}

/** Entry of the transcript list (from the search index of the engine, without reading every file). */
data class TranscriptSummary(
    val id: String,
    val title: String,
    val created: String,
    val duration: Double,
    val folder: String?,
    val modelName: String,
    val preview: String,
    val speakers: Int,
    val words: Int,
    val hasAudio: Boolean,
    val status: TranscriptStatus = TranscriptStatus.RAW,
    val wordTimes: Boolean = false,
) {
    companion object {
        fun parseList(json: String): List<TranscriptSummary> = runCatching {
            SpeechJson.parseToJsonElement(json).jsonArray.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                TranscriptSummary(
                    id = o.str("id") ?: return@mapNotNull null,
                    title = o.str("title").orEmpty().ifBlank { "Transkript" },
                    created = o.str("created").orEmpty(),
                    duration = o.num("duration") ?: 0.0,
                    folder = o.str("folder")?.takeIf { it.isNotBlank() },
                    modelName = o.str("model_name").orEmpty(),
                    preview = o.str("preview").orEmpty(),
                    speakers = o.int("speakers") ?: 0,
                    words = o.int("words") ?: 0,
                    hasAudio = o.bool("audio") == true,
                    status = TranscriptStatus.of(o.str("status")),
                    wordTimes = o.bool("word_times") == true,
                )
            }.sortedByDescending { it.created }
        }.logFailure("Transkriptliste lesen").getOrDefault(emptyList())
    }
}

/** A recognition model the user can load (Whisper Turbo, Parakeet …). */
data class SpeechModel(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val installed: Boolean,
    /** 0..1 while downloading, null otherwise. */
    val downloading: Double?,
    val canTranslate: Boolean,
    val wordTimes: Boolean,
    /** Seconds of computing per second of audio, measured on this device. */
    val speed: Double?,
    /** File names and URLs for loading them by hand (company networks). */
    val files: List<Pair<String, String>>,
)

/** A transcription in the queue or just finished. */
data class SpeechJob(
    val id: String,
    val name: String,
    val status: String,
    val stage: String,
    val progress: Double,
    val eta: Double?,
    val error: String?,
    val notice: String?,
    val transcriptId: String?,
    val segmentCount: Int,
) {
    val active get() = status == "queued" || status == "running"

    companion object {
        fun parse(o: JsonObject) = SpeechJob(
            id = o.str("id").orEmpty(),
            name = o.str("name").orEmpty().substringBeforeLast('.').ifBlank { o.str("name").orEmpty() },
            status = o.str("status").orEmpty(),
            stage = o.str("stage").orEmpty(),
            progress = o.num("progress") ?: 0.0,
            eta = o.num("eta"),
            error = o.str("error"),
            notice = o.str("notice"),
            transcriptId = o.str("transcript_id"),
            segmentCount = o.int("segment_count") ?: 0,
        )
    }
}

/** Recording in progress. */
data class RecordingState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val seconds: Double = 0.0,
    val level: Float = 0f,
    val marks: List<Mark> = emptyList(),
    /** Text recognized while recording (live). */
    val liveText: List<String> = emptyList(),
    val liveModel: String? = null,
)

/** A file offered for transcription (shared, picked, or a recording rescued after a crash). */
data class IncomingFile(val token: String, val name: String, val size: Long, val duration: Double?, val recovered: Boolean)

/** Everything the screens show about the speech engine. */
data class SpeechState(
    val models: List<SpeechModel> = emptyList(),
    val jobs: List<SpeechJob> = emptyList(),
    val recording: RecordingState = RecordingState(),
    val speakerModelReady: Boolean = false,
    val calibrated: Boolean = false,
    val threads: Int = 0,
    val storage: Long = 0,
    val modelsSize: Long = 0,
    val modelsDir: String = "",
) {
    companion object {
        fun parse(json: String, previous: SpeechState): SpeechState = runCatching {
            val o = SpeechJson.parseToJsonElement(json).jsonObject
            val speed = o.obj("speed")
            val rec = o.obj("recording")
            SpeechState(
                models = o.arr("models").orEmpty().mapNotNull { el ->
                    val m = el as? JsonObject ?: return@mapNotNull null
                    SpeechModel(
                        id = m.str("id").orEmpty(),
                        name = m.str("name").orEmpty(),
                        description = m.str("desc").orEmpty(),
                        size = m.long("size") ?: 0,
                        installed = m.bool("installed") == true,
                        downloading = m.num("downloading"),
                        canTranslate = m.bool("can_translate") == true,
                        wordTimes = m.bool("word_times") == true,
                        speed = m.num("speed") ?: speed?.num(m.str("id").orEmpty()),
                        files = m.arr("files").orEmpty().mapNotNull { f ->
                            val x = f as? JsonObject ?: return@mapNotNull null
                            (x.str("name") ?: return@mapNotNull null) to x.str("url").orEmpty()
                        },
                    )
                },
                jobs = o.arr("jobs").orEmpty().mapNotNull { (it as? JsonObject)?.let(SpeechJob::parse) },
                recording = rec?.let { r ->
                    RecordingState(
                        active = r.bool("active") == true,
                        paused = r.bool("paused") == true,
                        seconds = r.num("seconds") ?: 0.0,
                        level = previous.recording.level,
                        marks = r.arr("marks").orEmpty().mapNotNull { m ->
                            val x = m as? JsonObject ?: return@mapNotNull null
                            Mark(x.num("t") ?: 0.0, x.str("label").orEmpty())
                        },
                        liveText = r.obj("live")?.arr("segments").orEmpty().mapNotNull { (it as? JsonObject)?.str("text") },
                        liveModel = r.obj("live")?.str("model_name"),
                    )
                } ?: RecordingState(),
                speakerModelReady = o.bool("speaker_model") == true,
                calibrated = o.bool("calibrated") == true,
                threads = o.int("threads") ?: 0,
                storage = o.long("storage") ?: 0,
                modelsSize = o.long("models_size") ?: 0,
                modelsDir = o.str("models_dir").orEmpty(),
            )
        }.logFailure("Status der Sprach-Engine").getOrDefault(previous)
    }
}

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
