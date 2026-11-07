package io.github.thepytales.llmhub.docs.text

/**
 * Änderungen nachverfolgen im Fließtext (AP-44): the changes between the text when tracking began ([base]) and now
 * ([current]), word by word. Each change is a [Hunk] – removed text of the base and inserted text of now at the same
 * place –, shown in the running text (inserted green, removed struck through) and accepted or rejected one by one.
 */
object TrackDiff {
    /** [del]: removed range of the base text; [ins]: inserted range of the current text (either may be empty). */
    data class Hunk(val del: IntRange, val ins: IntRange) {
        /** Where the removed text shows in the current text. */
        val at: Int get() = ins.first
    }

    /** Words, runs of spaces, line breaks and other characters, each on its own. */
    fun tokens(s: String): List<String> = Regex("\\n|[\\p{L}\\p{N}_]+|[ \\t]+|[^\\p{L}\\p{N}_\\s]").findAll(s).map { it.value }.toList()

    /** The changes from [base] to [current] (empty when they are equal). */
    fun hunks(base: String, current: String): List<Hunk> {
        if (base == current) return emptyList()
        val a = tokens(base)
        val b = tokens(current)
        val ops = diff(a, b) ?: return listOf(Hunk(0 until base.length, 0 until current.length))
        // Walk the edit script (0 keep, 1 delete from a, 2 insert from b); neighbouring edits make one change.
        val out = mutableListOf<Hunk>()
        var pa = 0
        var pb = 0
        var ia = 0
        var ib = 0
        var k = 0
        while (k < ops.size) {
            if (ops[k] == 0) {
                pa += a[ia++].length
                pb += b[ib++].length
                k++
                continue
            }
            val sa = pa
            val sb = pb
            while (k < ops.size && ops[k] != 0) {
                if (ops[k] == 1) pa += a[ia++].length else pb += b[ib++].length
                k++
            }
            out += Hunk(sa until pa, sb until pb)
        }
        return out
    }

    /**
     * Myers' diff of two token lists: 0 = keep, 1 = delete, 2 = insert; null when they differ in more than [maxD]
     * places (then the whole text counts as one change).
     */
    fun diff(a: List<String>, b: List<String>, maxD: Int = 3000): IntArray? {
        // Common start and end first: most edits are local.
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
        val n = a.size - pre - suf
        val m = b.size - pre - suf
        val mid = when {
            n == 0 -> IntArray(m) { 2 }
            m == 0 -> IntArray(n) { 1 }
            else -> myers(a, b, pre, n, m, maxD) ?: return null
        }
        return IntArray(pre) + mid + IntArray(suf)
    }

    private fun myers(a: List<String>, b: List<String>, off: Int, n: Int, m: Int, maxD: Int): IntArray? {
        val max = n + m
        val o = max + 1
        val v = IntArray(2 * max + 3)
        // Before step d: the band k = -d-1 … d+1 of v (all that step d reads), for the way back.
        val trace = ArrayList<IntArray>()
        var end = -1
        search@ for (d in 0..minOf(max, maxD)) {
            trace += v.copyOfRange(o - d - 1, o + d + 2)
            for (k in -d..d step 2) {
                var x = if (k == -d || k != d && v[o + k - 1] < v[o + k + 1]) v[o + k + 1] else v[o + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[off + x] == b[off + y]) { x++; y++ }
                v[o + k] = x
                if (x >= n && y >= m) { end = d; break@search }
            }
        }
        if (end < 0) return null
        val ops = ArrayList<Int>(n + m)
        var x = n
        var y = m
        for (d in end downTo 0) {
            val band = trace[d]
            fun at(k: Int) = band[k + d + 1]
            val k = x - y
            val pk = if (k == -d || k != d && at(k - 1) < at(k + 1)) k + 1 else k - 1
            val px = if (d == 0) 0 else at(pk)
            val py = px - pk
            while (x > px && y > py) { ops += 0; x--; y-- }
            if (d > 0) ops += if (x == px) 2 else 1
            x = px
            y = py
        }
        ops.reverse()
        return ops.toIntArray()
    }
}
