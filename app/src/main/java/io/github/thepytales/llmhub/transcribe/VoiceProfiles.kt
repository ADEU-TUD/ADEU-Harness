package io.github.thepytales.llmhub.transcribe

import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.ListStore
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/**
 * A voice the user named once (e.g. the interviewer in a series of interviews): the mean voice print of that
 * person. Kept only on this device; new recordings can be matched against it.
 */
@Serializable
data class VoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val vector: List<Float>,
    /** From how many recordings the voice print is averaged. */
    val recordings: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

class VoiceProfileStore(dir: File) : ListStore<VoiceProfile>(FileBackend(File(dir, "voices.json")), VoiceProfile.serializer(), { it.id }) {
    /** Adds the voice of [name] or averages it into the existing profile of that name. */
    fun learn(name: String, vector: FloatArray) {
        val existing = current.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing == null) {
            upsert(VoiceProfile(name = name, vector = vector.toList()))
        } else {
            val n = existing.recordings
            val mixed = existing.vector.mapIndexed { i, v -> (v * n + vector.getOrElse(i) { v }) / (n + 1) }
            upsert(existing.copy(vector = mixed, recordings = n + 1, updatedAt = System.currentTimeMillis()))
        }
    }
}

object Voices {
    /** Above this cosine similarity two voice prints count as the same person (ResNet34 voice prints). */
    const val THRESHOLD = 0.55

    fun cosine(a: FloatArray, b: List<Float>): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val y = b.getOrElse(i) { 0f }
            dot += a[i] * y; na += a[i] * a[i]; nb += y * y
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
    }

    data class Match(val speaker: Int, val profile: VoiceProfile, val similarity: Double)

    /** Best profile for each speaker, every profile at most once (most similar pairs first). */
    fun match(voices: Map<Int, FloatArray>, profiles: List<VoiceProfile>): List<Match> {
        val pairs = voices.flatMap { (k, v) -> profiles.map { p -> Match(k, p, cosine(v, p.vector)) } }
            .filter { it.similarity >= THRESHOLD }.sortedByDescending { it.similarity }
        val out = mutableListOf<Match>()
        for (p in pairs) if (out.none { it.speaker == p.speaker || it.profile.id == p.profile.id }) out += p
        return out.sortedBy { it.speaker }
    }
}
