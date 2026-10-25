package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ScriptRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Interrater reliability in the sandbox against published values. */
class ReliabilityTest {
    private fun js(code: String): String {
        val r = ScriptRunner.run(code)
        assertNull(r.error, r.error)
        return r.output.trim()
    }

    /** Krippendorff (2011), "Computing Krippendorff's Alpha-Reliability": 4 coders, 12 units, missing values. */
    private val krippendorff = """
        var d = [[1,1,null,1],[2,2,3,2],[3,3,3,3],[3,3,3,3],[2,2,2,2],[1,2,3,4],[4,4,4,4],[1,1,2,1],[2,2,2,2],[null,5,5,5],[null,null,1,1],[null,3,null,null]];
    """

    @Test
    fun krippendorffAlphaMatchesThePaper() {
        assertEquals("0.743", js("$krippendorff print(krippendorffAlpha(d, 'nominal').alpha.toFixed(3))"))
        assertEquals("0.815", js("$krippendorff print(krippendorffAlpha(d, 'ordinal').alpha.toFixed(3))"))
        assertEquals("0.849", js("$krippendorff print(krippendorffAlpha(d, 'interval').alpha.toFixed(3))"))
        assertEquals("0.797", js("$krippendorff print(krippendorffAlpha(d, 'ratio').alpha.toFixed(3))"))
    }

    @Test
    fun cohensKappa() {
        // 50 units: yes/yes 20, yes/no 5, no/yes 10, no/no 15 → po = 0.70, pe = 0.50, kappa = 0.40
        val code = """
            var a = [], b = [];
            function add(x, y, n) { for (var i = 0; i < n; i++) { a.push(x); b.push(y); } }
            add('ja','ja',20); add('ja','nein',5); add('nein','ja',10); add('nein','nein',15);
            var k = cohensKappa(a, b);
            print(k.kappa + ' ' + k.observed + ' ' + k.expected + ' ' + k.interpretation + ' ' + agreement(a, b).percent);
        """
        assertEquals("0.4 0.7 0.5 ausreichend 70", js(code))
    }

    @Test
    fun weightedKappaCountsNearMisses() {
        val code = """
            var a = [1,2,3,4,5,1,2,3,4,5], b = [1,2,3,4,5,2,3,4,5,4];
            print(cohensKappa(a, b).kappa < cohensKappa(a, b, 'quadratic').kappa);
        """
        assertEquals("true", js(code))
    }

    /** Example of Fleiss (1971) as on Wikipedia: 10 units, 14 raters, 5 categories → kappa 0.210. */
    @Test
    fun fleissKappa() {
        val code = """
            var counts = [[0,0,0,0,14],[0,2,6,4,2],[0,0,3,5,6],[0,3,9,2,0],[2,2,8,1,1],[7,7,0,0,0],[3,2,6,3,0],[2,5,3,2,2],[6,5,2,1,0],[0,2,2,3,7]];
            var ratings = counts.map(function(row) { var r = []; row.forEach(function(n, c) { for (var i = 0; i < n; i++) r.push('K' + (c + 1)); }); return r; });
            print(fleissKappa(ratings).kappa.toFixed(3));
        """
        assertEquals("0.210", js(code))
    }

    @Test
    fun unitsFromTable() {
        val code = """
            var rows = parseCSV('Segment;A;B\n1;Lob;Lob\n2;Kritik;Lob\n3;Frage;Frage\n4;Lob;Lob');
            var k = krippendorffAlpha(units(rows, ['A', 'B']));
            print(k.units + ' ' + (k.alpha > 0.4 && k.alpha < 0.8));
        """
        assertEquals("4 true", js(code))
    }
}
