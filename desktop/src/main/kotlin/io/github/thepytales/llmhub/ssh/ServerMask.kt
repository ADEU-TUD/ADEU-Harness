package io.github.thepytales.llmhub.ssh

/**
 * Pseudonymization for the Server-Terminal (AP-118): before a request, output or command goes to a model on a server,
 * this replaces what identifies the server or opens it – host names, IP addresses, user names, e-mail addresses and the
 * application folder get placeholders (`<host-1>`, `<ip-1>`, `<benutzer-1>`, `<mail-1>`, `<app-ordner>`) that [unmask]
 * puts back into Lora's proposal; passwords, passphrases, private keys, tokens and `KEY=value` secrets become
 * `<geheim>` and are **never** put back. The app's name pseudonymization runs on top for people's names.
 */
class ServerMask(secrets: Collection<String> = emptyList(), profile: SshProfile? = null) {
    private val secrets = secrets.filter { it.length >= 3 }.sortedByDescending { it.length }
    /** Real value → placeholder (restorable kinds only). */
    private val known = LinkedHashMap<String, String>()
    private val counters = HashMap<String, Int>()

    init {
        profile?.let { p ->
            if (p.host.isNotBlank()) placeholder(if (IPV4.matches(p.host)) "ip" else "host", p.host)
            if (p.user.isNotBlank() && p.user !in COMMON_USERS) placeholder("benutzer", p.user)
            p.workDir.trim().trimEnd('/').takeIf { it.count { c -> c == '/' } >= 2 }?.let { known[it] = "<app-ordner>" }
        }
    }

    private fun placeholder(kind: String, real: String): String = known.getOrPut(real) {
        val n = (counters[kind] ?: 0) + 1
        counters[kind] = n
        "<$kind-$n>"
    }

    fun mask(text: String): String {
        var t = text
        t = PRIVATE_KEY.replace(t, SECRET)
        for (s in secrets) t = t.replace(s, SECRET)
        t = URL_CREDENTIALS.replace(t) { m -> m.groupValues[1] + placeholder("benutzer", m.groupValues[2]) + ":" + SECRET + "@" }
        t = KEY_VALUE.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + SECRET }
        t = TOKEN.replace(t) { m -> if (looksLikeSecret(m.value)) SECRET else m.value }
        // Restorable details: the values of the profile first (longest first), then what the patterns find.
        // Whole words only and never inside a placeholder: a user „app“ must not turn „<app-ordner>“ into nonsense.
        for ((real, ph) in known.entries.sortedByDescending { it.key.length }) t = Regex("""(?<![\w<.-])""" + Regex.escape(real) + """(?![\w>-])""").replace(t, Regex.escapeReplacement(ph))
        t = MAIL.replace(t) { m -> placeholder("mail", m.value) }
        t = IPV4.replace(t) { m -> if (m.value.startsWith("127.") || m.value == "0.0.0.0") m.value else placeholder("ip", m.value) }
        t = IPV6.replace(t) { m -> if (m.value == "::1") m.value else placeholder("ip", m.value) }
        t = HOST.replace(t) { m -> if (PUBLIC.any { m.value.lowercase() == it || m.value.lowercase().endsWith(".$it") }) m.value else placeholder("host", m.value) }
        t = HOME.replace(t) { m -> if (m.groupValues[1] in COMMON_USERS || m.groupValues[1].startsWith("<")) m.value else "/home/" + placeholder("benutzer", m.groupValues[1]) }
        return t
    }

    /** Puts the restorable details back (a proposal of the model); `<geheim>` stays. */
    fun unmask(text: String): String {
        var t = text
        for ((real, ph) in known.entries.sortedByDescending { it.value.length }) t = t.replace(ph, real)
        return t
    }

    /** What was replaced, as „<host-1> = …“ – shown only on this device, never sent. */
    fun legend(): List<Pair<String, String>> = known.entries.map { it.value to it.key }

    companion object {
        const val SECRET = "<geheim>"

        /** Only the secrets of [text] removed (protocol on this device): keys, tokens, `KEY=value`, URL passwords, [secrets]. */
        fun secretsOnly(text: String, secrets: Collection<String>): String {
            var t = PRIVATE_KEY.replace(text, SECRET)
            for (s in secrets.filter { it.length >= 3 }.sortedByDescending { it.length }) t = t.replace(s, SECRET)
            t = URL_CREDENTIALS.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + ":" + SECRET + "@" }
            t = KEY_VALUE.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + SECRET }
            return TOKEN.replace(t) { m -> if (looksLikeSecret(m.value)) SECRET else m.value }
        }
        private val PRIVATE_KEY = Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----|PuTTY-User-Key-File-[\s\S]*?Private-MAC: \S+""")
        private val URL_CREDENTIALS = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://)([^:/@\s]+):([^@\s]+)@""")
        private val KEY_VALUE = Regex("""(?i)\b([A-Z0-9_]*(?:PASSWORD|PASSWD|PWD|PASS|SECRET|TOKEN|API_?KEY|APIKEY|PRIVATE_KEY|AUTH|CREDENTIALS?|DSN)[A-Z0-9_]*)(\s*[=:]\s*["']?)(?!<geheim>)[^\s"']+""")
        private val TOKEN = Regex("""\b(?:ghp|gho|ghs|github_pat|glpat|xox[abp]|sk|pk|rk)[-_][A-Za-z0-9_-]{16,}\b|\b[A-Za-z0-9+/=_-]{32,}\b""")
        private val MAIL = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
        private val IPV4 = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b""")
        private val IPV6 = Regex("""(?<![\w:])(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}(?![\w:])""")
        private val HOST = Regex("""\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?:de|com|org|net|eu|io|edu|info|local|lan|intern|internal|cloud|at|ch)\b""")
        private val HOME = Regex("""/home/([A-Za-z0-9._-]+)""")
        private val COMMON_USERS = setOf("root", "www-data", "ubuntu", "debian", "admin", "deploy", "user", "nobody", "apache", "nginx")

        /** Addresses everyone knows; they help the model and say nothing about the server. */
        private val PUBLIC = listOf(
            "github.com", "gitlab.com", "packagist.org", "getcomposer.org", "symfony.com", "php.net", "npmjs.org", "npmjs.com",
            "letsencrypt.org", "apache.org", "debian.org", "ubuntu.com", "example.org", "example.com", "example.net", "localhost.localdomain",
        )

        /** Long strings that are tokens, not commit hashes or file names (hex hashes and paths stay readable). */
        private fun looksLikeSecret(s: String): Boolean {
            if (Regex("""^(?:ghp|gho|ghs|github_pat|glpat|xox[abp]|sk|pk|rk)[-_]""").containsMatchIn(s)) return true
            if (Regex("""^[0-9a-fA-F]+$""").matches(s)) return false
            if (s.contains('/') && s.count { it == '/' } > 2) return false
            return s.any { it.isDigit() } && s.any { it.isUpperCase() } && s.any { it.isLowerCase() }
        }
    }
}
