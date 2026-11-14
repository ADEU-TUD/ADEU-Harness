package io.github.thepytales.llmhub.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Textumbruch ums Bild (AP-125 e, like Word's „Quadrat“): a picture with `wrap=left|right` stands at that edge of the
 * text after it, and that text runs on beside it – the lines next to the picture are narrower, below it they have the
 * whole width again.
 *
 * The text itself is not changed: its lines beside the picture end where they would end at the narrower width (each
 * becomes a paragraph of its own in what is shown, so Compose breaks there) and, with the picture on the left, they
 * are indented by its width. Offsets stay the same, so cursor, selection and page plan need nothing new.
 */

/** A picture at the [side] ("left"/"right") of a text field: the room it takes ([width] with the gap, [height]) in px. */
data class ImageWrap(val side: String, val width: Float, val height: Float)

/**
 * [text] (as the field shows it, with its paragraph styles) laid out beside [wrap] in a field [fieldPx] wide: the lines
 * next to the picture end where they end at the narrower width; on the left they are indented. [pxPerSp]: px of 1 sp.
 */
internal fun wrapAround(text: AnnotatedString, measurer: TextMeasurer, style: TextStyle, fieldPx: Int, wrap: ImageWrap, pxPerSp: Float): AnnotatedString {
    val narrow = (fieldPx - wrap.width).roundToInt()
    if (narrow < 40 || text.isEmpty() || wrap.height <= 0f) return text
    // Without Silbentrennung beside the picture: a line there ends at a paragraph edge in what is shown, where Compose
    // would draw no hyphen.
    val plain = AnnotatedString(text.text, text.spanStyles, text.paragraphStyles.map {
        // Measured ragged: justified lines would already fill the width and leave no room to spread.
        val st = it.item.copy(hyphens = Hyphens.None)
        AnnotatedString.Range(if (st.textAlign == TextAlign.Justify) st.copy(textAlign = TextAlign.Start) else st, it.start, it.end)
    })
    val layout = measurer.measure(plain, style, constraints = Constraints(maxWidth = narrow))
    val breaks = ArrayList<Int>()
    var beside = 0
    for (i in 0 until layout.lineCount) {
        if (layout.getLineTop(i) >= wrap.height) break
        beside = layout.getLineEnd(i)
        breaks += beside
    }
    if (beside == 0) return text
    val lineEnds = HashMap<Int, Int>()
    for (i in 0 until layout.lineCount) { if (layout.getLineTop(i) >= wrap.height) break; lineEnds[layout.getLineEnd(i)] = i }
    val indent = wrap.width / pxPerSp
    val b = AnnotatedString.Builder(text.text)
    text.spanStyles.forEach { b.addStyle(it.item, it.start, it.end) }
    text.getStringAnnotations(0, text.length).forEach { b.addStringAnnotation(it.tag, it.item, it.start, it.end) }
    for (r in text.paragraphStyles) {
        // Where a line beside the picture ends inside this paragraph (not at its own end).
        val cuts = breaks.filter { it > r.start && it < r.end && text.text[it - 1] != '\n' }
        var a = r.start
        var first = true
        for (c in cuts + r.end) {
            if (c <= a) continue
            var st = r.item
            val ti = st.textIndent
            // A line that goes on a paragraph has no first-line indent of its own.
            if (!first) st = st.copy(textIndent = TextIndent(ti?.restLine ?: 0.sp, ti?.restLine ?: 0.sp))
            if (a < beside) st = st.copy(hyphens = Hyphens.None)
            if (wrap.side == "left" && a < beside) {
                val t = st.textIndent
                val f = t?.firstLine?.takeIf { it.isSp }?.value ?: 0f
                val rest = t?.restLine?.takeIf { it.isSp }?.value ?: 0f
                st = st.copy(textIndent = TextIndent((f + indent).sp, (rest + indent).sp))
            }
            b.addStyle(st, a, c)
            // Blocksatz beside the picture: each line there is a paragraph of its own in what is shown (Compose would
            // leave it ragged as a last line), so the missing room goes to its spaces, as justified text does it.
            if (c < r.end && st.textAlign == TextAlign.Justify) if (lineEnds.containsKey(c)) justify(b, text.text, layout, a, c, narrow, pxPerSp)
            a = c
            first = false
        }
    }
    return b.toAnnotatedString()
}

/**
 * Spreads the room left at the end of the line [from] until [to] of [layout] (the text laid out [narrow] px wide) over
 * its spaces, so the line ends at the edge like justified text. A little room stays, so the line never gets too long
 * for its width and breaks again.
 */
private fun justify(b: AnnotatedString.Builder, text: String, layout: TextLayoutResult, from: Int, to: Int, narrow: Int, pxPerSp: Float) {
    var end = to
    while (end > from && text[end - 1].isWhitespace()) end--
    if (end <= from) return
    val spaces = (from until end).filter { text[it] == ' ' }
    if (spaces.isEmpty()) return
    val right = layout.getBoundingBox(end - 1).right
    val extra = narrow - right - 2f
    // A line with very few words would get huge gaps; Word leaves such a line as it is, too.
    if (extra <= 0f || extra > narrow * 0.35f) return
    val each = extra / spaces.size / pxPerSp
    for (k in spaces) b.addStyle(SpanStyle(letterSpacing = each.sp), k, k + 1)
}
