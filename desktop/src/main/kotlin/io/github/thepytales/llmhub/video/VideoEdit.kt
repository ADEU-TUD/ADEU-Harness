package io.github.thepytales.llmhub.video

import kotlin.math.abs

/**
 * Editing of a [VideoProject] (AP-52, AP-53) as pure functions – the editor, Lora and the tests use the same ones:
 * the blade, insert and overwrite from the source monitor, ripple, roll and slip trimming, the magnet timeline
 * (everything after an edit on V1 moves along, deleting closes the gap), picture-in-picture, linking picture and sound
 * and synchronising a second sound source.
 */
object VideoEdit {
    private const val MIN = 0.2

    /** A thing on the timeline: a clip of V1, a picture on V2 …, a sound on A1 … */
    sealed interface Item { val id: String }
    data class Clip(override val id: String) : Item
    data class Layer(override val id: String) : Item
    data class Sound(override val id: String) : Item

    /** The track of [item]: "V1", "V2" …, "A1" … */
    fun trackOf(p: VideoProject, item: Item): String? = when (item) {
        is Clip -> "V1".takeIf { p.clips.any { it.id == item.id } }
        is Layer -> p.layers.firstOrNull { it.id == item.id }?.let { "V${it.track}" }
        is Sound -> p.audio.firstOrNull { it.id == item.id }?.let { "A${it.track}" }
    }

    fun locked(p: VideoProject, item: Item) = trackOf(p, item)?.let { p.track(it).locked } == true

    /**
     * Moves everything that starts at or after [at] by [delta] seconds – layers, unlinked sound, titles, subtitles and
     * markers (the clips of V1 follow each other anyway, linked sound follows its clip). Nothing goes before 0.
     * Locked tracks stay.
     */
    fun rippleAfter(p: VideoProject, at: Double, delta: Double): VideoProject {
        if (abs(delta) < 1e-9) return p
        fun move(s: Double) = if (s >= at - 1e-6) (s + delta).coerceAtLeast(0.0) else s
        return p.copy(
            layers = p.layers.map { if (p.track("V${it.track}").locked) it else it.copy(startSec = move(it.startSec)) },
            audio = p.audio.map { if (it.linkedTo != null || p.track("A${it.track}").locked) it else it.copy(startSec = move(it.startSec)) },
            overlays = p.overlays.map { it.copy(startSec = move(it.startSec)) },
            subtitles = p.subtitles.map { if (it.startSec >= at - 1e-6) { val s = move(it.startSec); it.copy(startSec = s, endSec = s + (it.endSec - it.startSec)) } else it },
            markers = p.markers.map { it.copy(sec = move(it.sec)) },
            markIn = p.markIn?.let { move(it) },
            markOut = p.markOut?.let { move(it) },
        ).linked()
    }

    /** End of V1 clip [i] on the timeline where the next one begins (with a transition, the next one starts earlier). */
    private fun nextStart(p: VideoProject, i: Int): Double {
        val s = p.starts()
        return if (i + 1 < p.clips.size) s[i + 1] else s[i] + p.clips[i].length
    }

    // ── Blade ──

    /** Splits whatever [item] is at timeline time [t] (the blade, B). Returns the project and the id of the right part. */
    fun split(p: VideoProject, item: Item, t: Double): Pair<VideoProject, String>? {
        if (locked(p, item)) return null
        return when (item) {
            is Clip -> {
                val i = p.clips.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: return null
                val c = p.clips[i]
                val local = t - p.starts()[i]
                if (local < MIN || c.length - local < MIN) return null
                // Speed, reverse and freeze (AP-54) map the cut to the source in their own way.
                val (a, b) = VideoFx.splitClip(c, local)
                // Sound linked to the clip goes with the right part where it lies after the cut.
                val q = p.copy(clips = p.clips.toMutableList().apply { set(i, a); add(i + 1, b) })
                val withSound = q.copy(audio = q.audio.flatMap { a ->
                    if (a.linkedTo != c.id) listOf(a) else listOf(a, a.copy(id = newVideoId(), linkedTo = b.id))
                })
                withSound.linked() to b.id
            }
            is Layer -> {
                val l = p.layers.firstOrNull { it.id == item.id } ?: return null
                val src = l.inSec + (t - l.startSec)
                if (src - l.inSec < MIN || l.outSec - src < MIN) return null
                val b = l.copy(id = newVideoId(), startSec = t, inSec = src)
                p.copy(layers = p.layers.flatMap { if (it.id == l.id) listOf(l.copy(outSec = src), b) else listOf(it) }) to b.id
            }
            is Sound -> {
                val a = p.audio.firstOrNull { it.id == item.id } ?: return null
                if (a.linkedTo != null) return split(p, Clip(a.linkedTo), t)
                val src = a.inSec + (t - a.startSec)
                if (src - a.inSec < MIN || a.outSec - src < MIN) return null
                val b = a.copy(id = newVideoId(), startSec = t, inSec = src)
                p.copy(audio = p.audio.flatMap { if (it.id == a.id) listOf(a.copy(outSec = src), b) else listOf(it) }) to b.id
            }
        }
    }

    /** Splits V1 at [t] (S, Strg+B); null when there is a cut already or no clip. */
    fun splitV1(p: VideoProject, t: Double): Pair<VideoProject, String>? {
        val (i, _) = p.at(t) ?: return null
        return split(p, Clip(p.clips[i].id), t)
    }

    // ── Delete ──

    /**
     * Deletes [item]. On V1 the clips after it close up; with the magnet the other tracks move along. On the other
     * tracks the magnet closes the gap on that track.
     */
    fun delete(p: VideoProject, item: Item): VideoProject {
        if (locked(p, item)) return p
        return when (item) {
            is Clip -> {
                val i = p.clips.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: return p
                val start = p.starts()[i]
                val end = nextStart(p, i)
                val q = p.copy(clips = p.clips.filterIndexed { k, _ -> k != i }, audio = p.audio.filter { it.linkedTo != item.id })
                val shift = q.starts().getOrNull(i)?.let { it - end } ?: (start - end)
                if (p.magnet) rippleAfter(q, end, shift) else q.linked()
            }
            is Layer -> {
                val l = p.layers.firstOrNull { it.id == item.id } ?: return p
                val q = p.copy(layers = p.layers.filter { it.id != l.id })
                if (!p.magnet) q else q.copy(layers = q.layers.map { if (it.track == l.track && it.startSec >= l.endSec - 1e-6) it.copy(startSec = (it.startSec - l.length).coerceAtLeast(0.0)) else it })
            }
            is Sound -> {
                val a = p.audio.firstOrNull { it.id == item.id } ?: return p
                val q = p.copy(audio = p.audio.filter { it.id != a.id })
                if (!p.magnet || a.linkedTo != null) q
                else q.copy(audio = q.audio.map { if (it.linkedTo == null && it.track == a.track && it.startSec >= a.endSec - 1e-6) it.copy(startSec = (it.startSec - a.length).coerceAtLeast(0.0)) else it })
            }
        }
    }

    /** Closes the gaps on track [track] ("V2" …, "A1" …): every piece moves to the end of the one before it. */
    fun closeGaps(p: VideoProject, track: String): VideoProject {
        val n = track.drop(1).toIntOrNull() ?: return p
        if (p.track(track).locked) return p
        return when (track.first()) {
            'V' -> {
                var t = 0.0
                val moved = p.layers.filter { it.track == n }.sortedBy { it.startSec }.associate { l -> l.id to t.also { t += l.length } }
                p.copy(layers = p.layers.map { l -> moved[l.id]?.let { l.copy(startSec = it) } ?: l })
            }
            else -> {
                var t = 0.0
                val moved = p.audio.filter { it.track == n && it.linkedTo == null }.sortedBy { it.startSec }.associate { a -> a.id to t.also { t += a.length } }
                p.copy(audio = p.audio.map { a -> moved[a.id]?.let { a.copy(startSec = it) } ?: a })
            }
        }
    }

    // ── Trimming ──

    /**
     * Ripple trim of a V1 clip: its [start] edge (true) or end edge moves by [delta] seconds of the timeline (its In or
     * Out by delta × speed in the source; a reversed clip begins at its Out, a frozen frame only changes its length);
     * the clips after it close up and, with the magnet, everything else after it moves along. [sourceLength] limits Out.
     */
    fun rippleTrim(p: VideoProject, id: String, start: Boolean, delta: Double, sourceLength: Double? = null): VideoProject {
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return p
        if (p.track("V1").locked) return p
        val c = p.clips[i]
        val max = sourceLength?.takeIf { it > 0.05 } ?: Double.MAX_VALUE
        val d = delta * c.speed
        val next = when {
            c.fx.freeze -> c.copy(outSec = (c.outSec + if (start) -delta else delta).coerceAtLeast(c.inSec + MIN))
            c.fx.reverse && start -> c.copy(outSec = (c.outSec - d).coerceIn(c.inSec + MIN, max))
            c.fx.reverse -> c.copy(inSec = (c.inSec - d).coerceIn(0.0, c.outSec - MIN))
            start -> c.copy(inSec = (c.inSec + d).coerceIn(0.0, c.outSec - MIN))
            else -> c.copy(outSec = (c.outSec + d).coerceIn(c.inSec + MIN, max))
        }
        val change = next.length - c.length
        if (abs(change) < 1e-9) return p
        val oldEnd = nextStart(p, i)
        val q = p.copy(clips = p.clips.toMutableList().apply { set(i, next) })
        return if (p.magnet) rippleAfter(q, oldEnd, change) else q.linked()
    }

    /**
     * Roll trim: the cut between V1 clip [id] and the next one moves by [delta] – the first gets longer, the second
     * shorter (or the other way round); nothing else moves.
     */
    fun roll(p: VideoProject, id: String, delta: Double, sourceLength: Double? = null): VideoProject {
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 && it + 1 < p.clips.size } ?: return p
        if (p.track("V1").locked) return p
        val a = p.clips[i]
        val b = p.clips[i + 1]
        // With effects (AP-54) the cut moves by timeline seconds; a reversed or frozen clip has no plain Out/In to roll.
        if (a.fx.reverse || a.fx.freeze || b.fx.reverse || b.fx.freeze) return p
        val maxA = sourceLength?.takeIf { it > 0.05 }?.let { (it - a.outSec) / a.speed } ?: Double.MAX_VALUE
        val d = delta.coerceIn(maxOf(-(a.length - MIN), -b.inSec / b.speed), minOf(b.length - MIN, maxA))
        if (abs(d) < 1e-9) return p
        return p.copy(clips = p.clips.toMutableList().apply { set(i, a.copy(outSec = a.outSec + d * a.speed)); set(i + 1, b.copy(inSec = b.inSec + d * b.speed)) }).linked()
    }

    /** Slip: the clip keeps its place and length, the piece of the source moves by [delta] (In and Out together). */
    fun slip(p: VideoProject, item: Item, delta: Double, sourceLength: Double? = null): VideoProject {
        if (locked(p, item)) return p
        val max = sourceLength?.takeIf { it > 0.05 } ?: Double.MAX_VALUE
        fun d(inS: Double, outS: Double) = delta.coerceIn(-inS, max - outS)
        return when (item) {
            is Clip -> p.copy(clips = p.clips.map { c -> if (c.id != item.id) c else d(c.inSec, c.outSec).let { c.copy(inSec = c.inSec + it, outSec = c.outSec + it) } }).linked()
            is Layer -> p.copy(layers = p.layers.map { l -> if (l.id != item.id) l else d(l.inSec, l.outSec).let { l.copy(inSec = l.inSec + it, outSec = l.outSec + it) } })
            is Sound -> p.copy(audio = p.audio.map { a -> if (a.id != item.id || a.linkedTo != null) a else d(a.inSec, a.outSec).let { a.copy(inSec = a.inSec + it, outSec = a.outSec + it) } })
        }
    }

    // ── Source monitor: insert and overwrite ──

    /**
     * Inserts [clip] into V1 at timeline time [at] (F9): a clip under [at] is split, everything after moves on by the
     * length of the new clip (other tracks with the magnet).
     */
    fun insert(p: VideoProject, clip: VideoClip, at: Double): VideoProject {
        if (p.track("V1").locked) return p
        val c = clip.copy(transition = "none")
        if (p.clips.isEmpty()) return p.copy(clips = listOf(c))
        val t = at.coerceIn(0.0, p.duration)
        val split = splitV1(p, t)?.first ?: p
        val s = split.starts()
        // Index of the first clip starting at or after t.
        val index = s.indexOfFirst { it >= t - 1e-3 }.let { if (it < 0) split.clips.size else it }
        val q = split.copy(clips = split.clips.toMutableList().apply { add(index, c) })
        val insertedAt = q.starts()[index]
        return if (p.magnet) rippleAfter(q, insertedAt, c.length) else q.linked()
    }

    /**
     * Overwrites V1 from [at] for the length of [clip] (F10): what lies there is cut out and replaced, the rest stays
     * where it is (the video gets longer only if the clip reaches past the end).
     */
    fun overwrite(p: VideoProject, clip: VideoClip, at: Double): VideoProject {
        if (p.track("V1").locked) return p
        val c = clip.copy(transition = "none")
        if (p.clips.isEmpty()) return p.copy(clips = listOf(c))
        val t = at.coerceIn(0.0, p.duration)
        val end = t + c.length
        var q = splitV1(p, end)?.first ?: p
        q = splitV1(q, t)?.first ?: q
        val s = q.starts()
        val keep = q.clips.filterIndexed { k, k0 -> !(s[k] >= t - 1e-3 && s[k] + k0.length <= end + 1e-3) }
        val index = keep.indexOfFirst { k -> q.starts()[q.clips.indexOf(k)] >= t - 1e-3 }.let { if (it < 0) keep.size else it }
        val removedIds = q.clips.map { it.id }.toSet() - keep.map { it.id }.toSet()
        val next = q.copy(
            clips = keep.toMutableList().apply { add(index, c) }.mapIndexed { k, x -> if (k == index + 1) x.copy(transition = "none") else x },
            audio = q.audio.filter { it.linkedTo == null || it.linkedTo !in removedIds },
        )
        return next.linked()
    }

    // ── Moving on free tracks ──

    /** Moves a picture of V2 … to [start] and track [track] (≥ 2). */
    fun moveLayer(p: VideoProject, id: String, start: Double, track: Int): VideoProject {
        val l = p.layers.firstOrNull { it.id == id } ?: return p
        if (p.track("V${l.track}").locked || p.track("V$track").locked) return p
        val tr = track.coerceAtLeast(2)
        return p.copy(layers = p.layers.map { if (it.id == id) it.copy(startSec = start.coerceAtLeast(0.0), track = tr) else it }, videoTracks = maxOf(p.videoTracks, tr))
    }

    /** Moves a sound to [start] and track [track] (≥ 1). Linked sound only changes its track. */
    fun moveSound(p: VideoProject, id: String, start: Double, track: Int): VideoProject {
        val a = p.audio.firstOrNull { it.id == id } ?: return p
        if (p.track("A${a.track}").locked || p.track("A$track").locked) return p
        val tr = track.coerceAtLeast(1)
        return p.copy(audio = p.audio.map { if (it.id == id) (if (it.linkedTo != null) it.copy(track = tr) else it.copy(startSec = start.coerceAtLeast(0.0), track = tr)) else it }, audioTracks = maxOf(p.audioTracks, tr))
    }

    /** Edges a dragged piece snaps to with the magnet: cuts of V1, starts and ends on the tracks, markers, In/Out. */
    fun snapPoints(p: VideoProject, except: String? = null): List<Double> {
        val s = p.starts()
        return buildList {
            add(0.0)
            p.clips.forEachIndexed { i, c -> add(s[i]); add(s[i] + c.length) }
            p.layers.filter { it.id != except }.forEach { add(it.startSec); add(it.endSec) }
            p.audio.filter { it.id != except }.forEach { add(it.startSec); add(it.endSec) }
            p.overlays.forEach { add(it.startSec); add(it.endSec) }
            p.markers.forEach { add(it.sec) }
            p.markIn?.let { add(it) }
            p.markOut?.let { add(it) }
        }
    }

    /** [t] snapped to the nearest of [points] within [tolerance] seconds, otherwise [t]. */
    fun snap(t: Double, points: List<Double>, tolerance: Double): Double =
        points.minByOrNull { abs(it - t) }?.takeIf { abs(it - t) <= tolerance } ?: t

    // ── Picture-in-picture ──

    /** Puts [path] on the lowest free video track above V1 from [at] (webcam corner [corner]). */
    fun addLayer(p: VideoProject, path: String, at: Double, length: Double, corner: String = "unten-rechts", still: Boolean = false): Pair<VideoProject, String> {
        val end = at + length
        val track = (2..p.videoTrackCount + 1).first { n -> p.layers.none { it.track == n && it.startSec < end - 1e-6 && it.endSec > at + 1e-6 } }
        val l = LayerClip(path = path, track = track, startSec = at, inSec = 0.0, outSec = length, frame = Frame.corner(corner), muted = still)
        return p.copy(layers = p.layers + l, videoTracks = maxOf(p.videoTracks, track)) to l.id
    }

    /** Lowest audio track without anything between [from] and [to] (a new one if all are taken). */
    fun freeAudioTrack(p: VideoProject, from: Double, to: Double, except: Set<String> = emptySet()): Int =
        (1..p.audioTrackCount + 1).first { n -> p.audio.none { it.id !in except && it.track == n && it.startSec < to - 1e-6 && it.endSec > from + 1e-6 } }

    // ── Linking picture and sound ──

    /**
     * Lays the sound of V1 clip [clipId] out onto an audio track, linked to the clip (it moves and is trimmed with it);
     * the clip itself becomes silent. Returns the project and the id of the sound.
     */
    fun detachSound(p: VideoProject, clipId: String): Pair<VideoProject, String>? {
        val i = p.clips.indexOfFirst { it.id == clipId }.takeIf { it >= 0 } ?: return null
        val c = p.clips[i]
        if (c.muted) return null
        val start = p.starts()[i]
        val track = freeAudioTrack(p, start, start + c.length)
        val a = AudioClip(path = c.path, startSec = start, inSec = c.inSec, outSec = c.outSec, volume = c.volume, fadeSec = 0.0, track = track, linkedTo = c.id, linkOffset = 0.0)
        val q = p.copy(clips = p.clips.map { if (it.id == c.id) it.copy(muted = true) else it }, audio = p.audio + a, audioTracks = maxOf(p.audioTracks, track))
        return q.linked() to a.id
    }

    /**
     * Links sound [soundId] to V1 clip [clipId]: the sound's source time is the clip's plus [offset] seconds (from
     * [AudioSync], or as it lies now when null). [muteClip]: the camera sound is switched off (the microphone is better).
     */
    fun link(p: VideoProject, clipId: String, soundId: String, offset: Double? = null, muteClip: Boolean = false): VideoProject {
        val i = p.clips.indexOfFirst { it.id == clipId }.takeIf { it >= 0 } ?: return p
        val c = p.clips[i]
        val a = p.audio.firstOrNull { it.id == soundId } ?: return p
        // As it lies now: the sound's source time at the clip's start minus the clip's In.
        val off = offset ?: (a.inSec + (p.starts()[i] - a.startSec) - c.inSec)
        return p.copy(
            clips = if (muteClip) p.clips.map { if (it.id == c.id) it.copy(muted = true) else it } else p.clips,
            audio = p.audio.map { if (it.id == a.id) it.copy(linkedTo = c.id, linkOffset = off, fadeSec = 0.0, volume = if (a.linkedTo == null && a.volume < 0.5f) 1f else a.volume) else it },
        ).linked()
    }

    /** Ends the link of sound [soundId]: it stays where it is. */
    fun unlink(p: VideoProject, soundId: String): VideoProject =
        p.copy(audio = p.audio.map { if (it.id == soundId) it.copy(linkedTo = null) else it })

    // ── Tracks ──

    fun setTrack(p: VideoProject, key: String, f: (TrackState) -> TrackState): VideoProject {
        val next = f(p.track(key))
        return p.copy(trackStates = if (next == TrackState()) p.trackStates - key else p.trackStates + (key to next))
    }

    fun addTrack(p: VideoProject, video: Boolean): VideoProject =
        if (video) p.copy(videoTracks = p.videoTrackCount + 1) else p.copy(audioTracks = p.audioTrackCount + 1)

    // ── Markers ──

    fun addMarker(p: VideoProject, at: Double, note: String = ""): Pair<VideoProject, String> {
        p.markers.firstOrNull { abs(it.sec - at) < 0.05 }?.let { return p to it.id }
        val m = Marker(sec = at.coerceAtLeast(0.0), note = note)
        return p.copy(markers = (p.markers + m).sortedBy { it.sec }) to m.id
    }

    /** Chapters from the markers (with a chapter at 0:00 as YouTube wants it): start, end and title. */
    fun chapters(p: VideoProject): List<Triple<Double, Double, String>> {
        val len = p.duration
        val ms = p.markers.filter { it.chapter && it.sec < len - 0.5 }.sortedBy { it.sec }
        if (ms.isEmpty()) return emptyList()
        val list = (if (ms.first().sec > 0.5) listOf(Marker(sec = 0.0, note = "Beginn")) else emptyList()) + ms
        return list.mapIndexed { i, m -> Triple(m.sec, list.getOrNull(i + 1)?.sec ?: len, m.note.ifBlank { "Kapitel ${i + 1}" }) }
    }

    /** The chapter list for the description of a YouTube video: „0:00 Beginn“, one per line. */
    fun youtubeChapters(p: VideoProject): String = chapters(p).joinToString("\n") { (s, _, title) ->
        val sec = s.toLong()
        (if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)) + " " + title
    }

    // ── Simple cuts for beginners (AP-121) ──

    /**
     * Trims the V1 clip under the playhead [t]: [start] removes everything of it before [t] („Anfang bis hier
     * kürzen“), otherwise everything from [t] on („Ende ab hier kürzen“). The clips after it close up; with the magnet
     * the other tracks move along (a ripple trim). Null when there is no clip under [t], [t] is at its edge already or
     * less than 0.2 s of it would stay.
     */
    fun trimToPlayhead(p: VideoProject, t: Double, start: Boolean): VideoProject? {
        if (p.track("V1").locked) return null
        val (i, _) = p.at(t) ?: return null
        val c = p.clips[i]
        val local = t - p.starts()[i]
        if (local < -1e-6 || local > c.length + 1e-6) return null
        val removed = if (start) local else c.length - local
        if (removed < 0.04 || c.length - removed < MIN) return null
        return rippleTrim(p, c.id, start, if (start) removed else -removed).takeIf { it != p }
    }

    /**
     * Cuts the piece [a]..[b] of the timeline out (AP-121, „Bereich herausschneiden“ between In and Out): the V1 clips
     * there are trimmed or split, linked sound goes with them, and everything after it moves along – also without the
     * magnet, so titles, music and markers stay with their picture. Frozen and reversed clips stay as they are.
     */
    fun cutRange(p: VideoProject, a: Double, b: Double): VideoProject {
        val from = minOf(a, b).coerceAtLeast(0.0)
        val to = maxOf(a, b).coerceAtMost(p.duration)
        if (to - from < 0.04 || p.track("V1").locked) return p
        val s = p.starts()
        val ranges = p.clips.indices.mapNotNull { i ->
            val c = p.clips[i]
            val x0 = maxOf(from, s[i])
            val x1 = minOf(to, s[i] + c.length)
            if (x1 - x0 < 0.04 || c.fx.freeze || c.fx.reverse) null
            else Triple(c.id, c.inSec + (x0 - s[i]) * c.speed, c.inSec + (x1 - s[i]) * c.speed)
        }
        if (ranges.isEmpty()) return p
        return TextCut.cut(p.copy(magnet = true), ranges).copy(magnet = p.magnet)
    }

    /** A copy of [p] to try something out (AP-121): new id, „(Kopie)“ after the title, the same files. */
    fun duplicate(p: VideoProject, suffix: String = "(Kopie)"): VideoProject {
        val now = System.currentTimeMillis()
        return p.copy(id = java.util.UUID.randomUUID().toString(), title = "${p.title} $suffix", createdAt = now, updatedAt = now)
    }

    // ── Media pool ──

    /** Adds [paths] to the pool (in folder [bin]); files already there stay once. */
    fun addToPool(p: VideoProject, paths: List<String>, bin: String = ""): VideoProject {
        val have = p.media.map { it.path }.toSet()
        return p.copy(media = p.media + paths.distinct().filter { it !in have }.map { PoolItem(path = it, bin = bin) })
    }

    /** The pool with the files that are used on the timeline but not in it yet (projects from before AP-53). */
    fun withPool(p: VideoProject): VideoProject =
        addToPool(p, p.clips.map { it.path } + p.layers.map { it.path } + p.audio.map { it.path })
}
