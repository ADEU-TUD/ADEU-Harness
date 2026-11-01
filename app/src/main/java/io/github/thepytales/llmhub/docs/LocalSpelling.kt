package io.github.thepytales.llmhub.docs

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import io.github.thepytales.llmhub.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Rechtschreibprüfung on the phone (AP-16): the spell checker of Android (usually the keyboard's, e.g. Gboard) for
 * German or English – on the device. Windows has its own version of this file.
 */
object LocalSpelling {
    data class Error(val start: Int, val length: Int, val word: String, val suggestions: List<String>)

    private fun locale(lang: String) = Locale.forLanguageTag(lang)

    private val learned = HashSet<String>()

    suspend fun available(lang: String = "de-DE"): Boolean = withContext(Dispatchers.Main) {
        val tsm = App.container.appContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager ?: return@withContext false
        val session = runCatching { tsm.newSpellCheckerSession(null, locale(lang), Listener {}, true) }.getOrNull()
        session?.close()
        session != null
    }

    private class Listener(val done: (Array<out SentenceSuggestionsInfo>?) -> Unit) : SpellCheckerSession.SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}
        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) = done(results)
    }

    suspend fun check(text: String, lang: String = "de-DE"): List<Error> {
        if (text.isBlank()) return emptyList()
        val tsm = App.container.appContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager ?: return emptyList()
        // The service checks a sentence at a time well; long texts go in pieces.
        val out = mutableListOf<Error>()
        var offset = 0
        for (piece in text.chunked(1500)) {
            val results = withTimeoutOrNull(8000) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Array<out SentenceSuggestionsInfo>?> { cont ->
                        val holder = arrayOfNulls<SpellCheckerSession>(1)
                        val created: SpellCheckerSession? = tsm.newSpellCheckerSession(null, locale(lang), Listener { r ->
                            holder[0]?.close()
                            if (cont.isActive) cont.resume(r)
                        }, true)
                        holder[0] = created
                        if (created == null) cont.resume(null) else created.getSentenceSuggestions(arrayOf(TextInfo(piece)), 5)
                    }
                }
            }
            results?.forEach { s ->
                for (k in 0 until s.suggestionsCount) {
                    val info = s.getSuggestionsInfoAt(k)
                    if (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO == 0) continue
                    val start = s.getOffsetAt(k)
                    val len = s.getLengthAt(k)
                    if (start < 0 || len <= 0 || start + len > piece.length) continue
                    val word = piece.substring(start, start + len)
                    if (word in learned) continue
                    out += Error(offset + start, len, word, (0 until info.suggestionsCount.coerceAtLeast(0)).map { info.getSuggestionAt(it) })
                }
            }
            offset += piece.length
        }
        return out
    }

    suspend fun learn(word: String, lang: String = "de-DE") {
        learned += word
    }
}
