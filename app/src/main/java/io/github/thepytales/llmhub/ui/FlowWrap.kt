package io.github.thepytales.llmhub.ui

import androidx.compose.ui.text.AnnotatedString
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
    val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = narrow))
    val breaks = ArrayList<Int>()
    var beside = 0
    for (i in 0 until layout.lineCount) {
        if (layout.getLineTop(i) >= wrap.height) break
        beside = layout.getLineEnd(i)
        breaks += beside
    }
    if (beside == 0) return text
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
            if (wrap.side == "left" && a < beside) {
                val t = st.textIndent
                val f = t?.firstLine?.takeIf { it.isSp }?.value ?: 0f
                val rest = t?.restLine?.takeIf { it.isSp }?.value ?: 0f
                st = st.copy(textIndent = TextIndent((f + indent).sp, (rest + indent).sp))
            }
            b.addStyle(st, a, c)
            a = c
            first = false
        }
    }
    return b.toAnnotatedString()
}
