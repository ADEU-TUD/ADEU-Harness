package io.github.thepytales.llmhub.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Narrowest preview between the side panels; below it a panel folds in (AP-70). */
internal val PREVIEW_MIN_WIDTH = 340.dp

/** Lowest area above the timeline (viewer with its bars). */
internal val UPPER_MIN_HEIGHT = 190.dp

/** Width of a [PanelResizer] strip. */
private val RESIZER = 10.dp

/**
 * The arrangement of the video editor (AP-70): widths of the media pool (or transcript) and the properties, height of
 * the timeline, which panels and tracks are folded in, the track height, the open sections of the properties, and
 * whether the app's side menu shows beside the editor. Kept in ui_state, so it survives a restart; [reset] puts
 * everything back. One arrangement for all projects, like a workspace in Resolve.
 */
internal class VideoLayout(private val prefs: SharedPreferences) {
    val pool = PanelWidth(prefs, "videoPoolWidth", 260.dp, 200.dp, 560.dp)
    val transcript = PanelWidth(prefs, "videoTranscriptWidth", 380.dp, 280.dp, 700.dp)
    val props = PanelWidth(prefs, "videoPropsWidth", 320.dp, 280.dp, 600.dp)
    /** Toolbar and tracks below the viewer. */
    val timeline = PanelWidth(prefs, "videoTimelineHeight", 300.dp, 150.dp, 1400.dp)

    private val stored = mutableListOf<Stored<*>>()

    var poolOpen by bool("videoPoolOpen", true)
    var propsOpen by bool("videoPropsOpen", true)
    /** „Vorschau groß“: both side panels step aside (they keep their own state). */
    var bigPreview by bool("videoBigPreview", false)
    /** The app's side menu beside the editor (folded in by default). */
    var menu by bool("videoMenu", false)
    /** „So entsteht dein Video“ in the properties (AP-121); ✕ folds it away, the help menu brings it back. */
    var guide by bool("videoGuide", true)
    /** "media" (media pool) or "text" (transcript, AP-55). */
    var leftTab by text("videoLeftTab", "media")
    /** Height of the tracks: 1 = as before, 0.6 … 2. */
    var trackScale by number("videoTrackScale", 1f)
    /** Lanes folded to a thin line: "T", "V2" …, "V1", "A1" …, "S". */
    var folded by text("videoFoldedTracks", "")
    /** Sections of the properties opened or closed by hand: "key=1;key=0" (the rest keeps its default). */
    private var sections by text("videoSections", "")

    /** The preview over the whole editor (Strg+F); not remembered. */
    var full by mutableStateOf(false)

    /** When both side panels do not fit: the one opened last stays (not remembered). */
    var preferPool by mutableStateOf(false)

    val foldedTracks: Set<String> get() = folded.split(',').filter { it.isNotBlank() }.toSet()

    fun toggleTrack(key: String) {
        val now = foldedTracks
        folded = (if (key in now) now - key else now + key).sorted().joinToString(",")
    }

    fun tracksTaller(taller: Boolean) {
        trackScale = (if (taller) trackScale * 1.25f else trackScale / 1.25f).coerceIn(MIN_TRACKS, MAX_TRACKS)
    }

    private fun sectionMap(): Map<String, Boolean> =
        sections.split(';').mapNotNull { e -> e.split('=').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to (it[1] == "1") } }.toMap()

    fun sectionOpen(key: String, default: Boolean): Boolean = sectionMap()[key] ?: default

    fun setSection(key: String, open: Boolean) {
        sections = (sectionMap() + (key to open)).entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${if (it.value) 1 else 0}" }
    }

    /** The header button of the media pool: folds it in, or brings it back (then it is the panel that stays). */
    fun togglePool(shown: Boolean) {
        if (shown) poolOpen = false else { poolOpen = true; preferPool = true; bigPreview = false }
    }

    fun toggleProps(shown: Boolean) {
        if (shown) propsOpen = false else { propsOpen = true; preferPool = false; bigPreview = false }
    }

    /** The left panel: the transcript is wider than the media pool and keeps its own width. */
    val left: PanelWidth get() = if (leftTab == "text") transcript else pool

    /** Back to the arrangement of a first start: widths, heights, panels, tracks and sections. */
    fun reset() {
        listOf(pool, transcript, props, timeline).forEach { it.reset() }
        stored.forEach { it.reset() }
        full = false
        preferPool = false
    }

    /** A remembered value of ui_state that the screen follows. */
    private inner class Stored<T>(
        val key: String,
        val default: T,
        read: (SharedPreferences, String, T) -> T,
        val write: (SharedPreferences.Editor, String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        var value by mutableStateOf(read(prefs, key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (this.value == value) return
            this.value = value
            prefs.edit().also { write(it, key, value) }.apply()
        }

        fun reset() {
            value = default
            prefs.edit().remove(key).apply()
        }
    }

    private fun bool(key: String, default: Boolean) =
        Stored(key, default, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) }).also { stored += it }

    private fun text(key: String, default: String) =
        Stored(key, default, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) }).also { stored += it }

    private fun number(key: String, default: Float) =
        Stored(key, default, { p, k, d -> p.getFloat(k, d).coerceIn(MIN_TRACKS, MAX_TRACKS) }, { e, k, v -> e.putFloat(k, v) }).also { stored += it }

    companion object {
        const val MIN_TRACKS = 0.6f
        const val MAX_TRACKS = 2f

        @Volatile
        private var instance: VideoLayout? = null

        /** One arrangement for the app (ui_state of this PC). */
        fun of(prefs: SharedPreferences): VideoLayout = instance?.takeIf { it.prefs === prefs } ?: synchronized(this) {
            instance?.takeIf { it.prefs === prefs } ?: VideoLayout(prefs).also { instance = it }
        }
    }
}

/** The side panels as shown: their widths, or null when folded in (asked for, or no room). */
internal data class PanelFit(val pool: Dp?, val props: Dp?)

/**
 * Which side panels fit beside a preview of at least [previewMin] in [width] (AP-70): first they get narrower down to
 * their minimum – the one not [preferPool] first –, then that one folds in. [pool] and [props] are (wanted, minimum)
 * of a panel that is open, null for a closed one.
 */
internal fun fitPanels(width: Dp, pool: Pair<Dp, Dp>?, props: Pair<Dp, Dp>?, previewMin: Dp = PREVIEW_MIN_WIDTH, preferPool: Boolean = false): PanelFit {
    val avail = width - previewMin - RESIZER * ((if (pool != null) 1 else 0) + (if (props != null) 1 else 0))
    var a = pool?.first?.coerceAtLeast(pool.second)
    var b = props?.first?.coerceAtLeast(props.second)
    fun excess() = (a ?: 0.dp) + (b ?: 0.dp) - avail
    if (excess() <= 0.dp) return PanelFit(a, b)
    // Narrower: the panel that may fold first gives up its width first.
    if (preferPool) {
        if (b != null) b = (b - excess()).coerceAtLeast(props!!.second)
        if (a != null && excess() > 0.dp) a = (a - excess()).coerceAtLeast(pool!!.second)
    } else {
        if (a != null) a = (a - excess()).coerceAtLeast(pool!!.second)
        if (b != null && excess() > 0.dp) b = (b - excess()).coerceAtLeast(props!!.second)
    }
    if (excess() <= 0.dp || a == null || b == null) return PanelFit(a, b)
    // Still too narrow for both: one folds in, the other takes what is there (at least its minimum).
    val rest = width - previewMin - RESIZER
    return if (preferPool) PanelFit(pool!!.first.coerceAtMost(rest).coerceAtLeast(pool.second), null)
    else PanelFit(null, props!!.first.coerceAtMost(rest).coerceAtLeast(props.second))
}

/** Height of the timeline in an editor body [height] high: the area above keeps at least [UPPER_MIN_HEIGHT]. */
internal fun fitTimeline(height: Dp, wanted: Dp, min: Dp): Dp =
    wanted.coerceAtMost(height - UPPER_MIN_HEIGHT - RESIZER).coerceAtLeast(min)

/** The arrangement of the open editor, for the properties' sections. */
internal val LocalVideoLayout = staticCompositionLocalOf<VideoLayout?> { null }

/** A group of symbol buttons with a light frame (tools, view, edit). */
@Composable
internal fun ToolGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Space between groups of a bar. */
@Composable
internal fun BarGap() = Spacer(Modifier.width(8.dp))

/** A bar of the editor (header, toolbar, viewer, transport): one line, never wrapping; the tag is for the tests. */
@Composable
internal fun EditorBar(tag: String, modifier: Modifier = Modifier, height: Dp = 40.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(height).testTag("video.bar.$tag").padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The head of a side panel: its title (or tabs) and the button that folds it in towards its edge. */
@Composable
internal fun PanelHead(tag: String, foldLabel: String, foldLeft: Boolean, onFold: () -> Unit, content: @Composable RowScope.() -> Unit) {
    EditorBar(tag, height = 40.dp) {
        content()
        Spacer(Modifier.weight(1f))
        TipIconButton(if (foldLeft) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.AutoMirrored.Outlined.KeyboardArrowRight, foldLabel, onFold, size = 32.dp)
    }
}

/**
 * A section of the properties (AP-70) that folds in and out, so only what fits the selected element is open; whether
 * it is open is remembered for its [key] in the arrangement. [summary] shows in the closed head (e.g. „2×“).
 */
@Composable
internal fun PropSection(key: String, title: String, defaultOpen: Boolean = true, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val layout = LocalVideoLayout.current
    var local by remember(key) { mutableStateOf(defaultOpen) }
    val open = layout?.sectionOpen(key, defaultOpen) ?: local
    fun toggle() { if (layout != null) layout.setSection(key, !open) else local = !open }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = tr(if (open) "Zuklappen" else "Aufklappen")) { toggle() }
                .semantics { role = Role.Button; stateDescription = tr(if (open) "aufgeklappt" else "zugeklappt") }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!open && !summary.isNullOrBlank()) {
                Text(summary, style = MaterialTheme.typography.labelSmall, color = LocalChatLook.current.brand, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp).padding(start = 6.dp))
            }
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** The project title in the header: one line, no big text box; a frame shows while it is edited. */
@Composable
internal fun TitleField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val outline = MaterialTheme.colorScheme.outline
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.onFocusChanged { focused = it.isFocused }.typing(),
        decorationBox = { inner ->
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (focused) outline.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

/** A horseshoe magnet (magnet timeline, N) – Material has none. */
internal val MagnetIcon: ImageVector by lazy {
    ImageVector.Builder("Magnet", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 4f); lineTo(9f, 4f); lineTo(9f, 12f)
            arcTo(3f, 3f, 0f, false, false, 15f, 12f)
            lineTo(15f, 4f); lineTo(19f, 4f); lineTo(19f, 12f)
            arcTo(7f, 7f, 0f, false, true, 5f, 12f)
            close()
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 8f); lineTo(9f, 8f)
            moveTo(15f, 8f); lineTo(19f, 8f)
        }
    }.build()
}

/** The symbol of a tool in the toolbar (AP-70). */
internal val CutTool.icon: ImageVector
    get() = when (this) {
        CutTool.SELECT -> Icons.Outlined.HighlightAlt
        CutTool.BLADE -> Icons.Outlined.ContentCut
        CutTool.TRIM -> Icons.Outlined.SwapHoriz
    }
