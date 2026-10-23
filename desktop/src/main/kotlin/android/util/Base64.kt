package android.util

/** Android's Base64 helper on top of java.util.Base64 (decoding ignores line breaks like Android). */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    fun encodeToString(input: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        val text = encoder.encodeToString(input)
        return if (flags and NO_WRAP != 0) text else text.chunked(76).joinToString("\n", postfix = "\n")
    }

    fun decode(str: String, flags: Int): ByteArray {
        val clean = str.filterNot { it.isWhitespace() }
        return if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder().decode(clean)
        else java.util.Base64.getMimeDecoder().decode(clean)
    }
}
