package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class GitHubTools(private val gh: GitHubClient) : ToolProvider {

    override val defs = listOf(
        ToolDef("list_repos", "Listet die GitHub-Repositories des Nutzers auf.", emptyList()),
        ToolDef(
            "select_repo", "Wählt das Repository aus, in dem gearbeitet wird. Setzt den Arbeitsbranch auf den Standardbranch. " +
                "Funktioniert auch für fremde öffentliche Repositories.",
            listOf(Param("full_name", "Repository im Format owner/name oder als GitHub-URL")),
        ),
        ToolDef(
            "list_files", "Listet alle Dateipfade im Arbeitsbranch (rekursiv). Optional nur unterhalb eines Pfads.",
            listOf(Param("path_prefix", "Optionaler Verzeichnispräfix, z.B. 'src/'", required = false)),
        ),
        ToolDef(
            "read_file", "Liest eine Datei aus dem Arbeitsbranch. Die Ausgabe enthält Zeilennummern.",
            listOf(
                Param("path", "Dateipfad relativ zum Repository-Root"),
                Param("start_line", "Erste Zeile (1-basiert, optional)", required = false, type = "integer"),
                Param("end_line", "Letzte Zeile (optional)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "search_code", "Durchsucht den Code des Repositories (GitHub-Codesuche, nur Standardbranch).",
            listOf(Param("query", "Suchbegriff")),
        ),
        ToolDef(
            "write_file", "Erstellt oder überschreibt eine Datei vollständig und committet sie in den Arbeitsbranch.",
            listOf(
                Param("path", "Dateipfad"),
                Param("content", "Vollständiger neuer Dateiinhalt"),
                Param("commit_message", "Commit-Nachricht"),
            ),
            writes = true,
        ),
        ToolDef(
            "edit_file", "Ersetzt einen exakt passenden, eindeutigen Textabschnitt in einer Datei und committet die Änderung. Bevorzugen für kleine Änderungen.",
            listOf(
                Param("path", "Dateipfad"),
                Param("old_text", "Exakter bestehender Text (muss genau einmal vorkommen)"),
                Param("new_text", "Neuer Text"),
                Param("commit_message", "Commit-Nachricht"),
            ),
            writes = true,
        ),
        ToolDef(
            "delete_file", "Löscht eine Datei im Arbeitsbranch.",
            listOf(Param("path", "Dateipfad"), Param("commit_message", "Commit-Nachricht")),
            writes = true,
        ),
        ToolDef("list_branches", "Listet die Branches des Repositories.", emptyList()),
        ToolDef(
            "create_branch", "Erstellt einen neuen Branch und wechselt dorthin.",
            listOf(
                Param("name", "Name des neuen Branches"),
                Param("from", "Ausgangsbranch (Standard: aktueller Arbeitsbranch)", required = false),
            ),
            writes = true,
        ),
        ToolDef("switch_branch", "Wechselt den Arbeitsbranch.", listOf(Param("name", "Branchname"))),
        ToolDef(
            "list_commits", "Zeigt die letzten Commits des Arbeitsbranches.",
            listOf(Param("count", "Anzahl (Standard 10)", required = false, type = "integer")),
        ),
        ToolDef(
            "create_pull_request", "Erstellt einen Pull Request vom Arbeitsbranch in den Basisbranch.",
            listOf(
                Param("title", "Titel"),
                Param("body", "Beschreibung (Markdown)", required = false),
                Param("base", "Zielbranch (Standard: Standardbranch)", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "list_pull_requests", "Listet Pull Requests.",
            listOf(Param("state", "open, closed oder all (Standard open)", required = false)),
        ),
        ToolDef(
            "list_issues", "Listet Issues des Repositories.",
            listOf(Param("state", "open, closed oder all (Standard open)", required = false)),
        ),
        ToolDef(
            "read_issue", "Liest ein Issue oder einen Pull Request inklusive Kommentaren.",
            listOf(Param("number", "Nummer", type = "integer")),
        ),
        ToolDef(
            "create_issue", "Erstellt ein neues Issue.",
            listOf(Param("title", "Titel"), Param("body", "Beschreibung", required = false)),
            writes = true,
        ),
        ToolDef(
            "comment_issue", "Kommentiert ein Issue oder einen Pull Request.",
            listOf(Param("number", "Nummer", type = "integer"), Param("body", "Kommentartext")),
            writes = true,
        ),
    )

    fun isWriteTool(name: String) = defs.firstOrNull { it.name == name }?.writes == true

    override fun needsConfirmation(name: String, args: JsonObject) = isWriteTool(name)

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext) = ToolOutput(dispatch(name, args, ctx))

    private fun need(ctx: RepoContext): Pair<String, String> {
        val repo = ctx.repo ?: throw IllegalStateException("Kein Repository ausgewählt. Nutze zuerst list_repos und select_repo.")
        return repo to (ctx.branch ?: "main")
    }

    private suspend fun dispatch(name: String, a: JsonObject, ctx: RepoContext): String {
        when (name) {
            "list_repos" -> return gh.listRepos().joinToString("\n") {
                "${it.fullName}${if (it.isPrivate) " (privat)" else ""} – ${it.description ?: ""}"
            }.ifEmpty { "Keine Repositories gefunden." }

            "select_repo" -> {
                val repo = parseRepo(a.req("full_name"))
                    ?: return "FEHLER: '${a.s("full_name")}' ist kein Repository im Format owner/name."
                val branch = gh.defaultBranch(repo)
                ctx.repo = repo
                ctx.branch = branch
                return "Repository $repo ausgewählt, Arbeitsbranch: $branch"
            }
        }
        val (repo, branch) = need(ctx)
        return when (name) {
            "list_files" -> {
                val res = gh.get("repos/$repo/git/trees/$branch", "recursive" to "1") as JsonObject
                val prefix = a.s("path_prefix")?.trim('/')?.let { if (it.isEmpty()) null else "$it/" }
                val paths = res["tree"]!!.jsonArray.map { it.jsonObject }
                    .filter { it.s("type") == "blob" }
                    .mapNotNull { it.s("path") }
                    .filter { prefix == null || it.startsWith(prefix) }
                val shown = paths.take(2000)
                buildString {
                    append("${paths.size} Dateien in $repo@$branch\n")
                    append(shown.joinToString("\n"))
                    if (paths.size > shown.size) append("\n…(${paths.size - shown.size} weitere)")
                    if (res.s("truncated") == "true") append("\n(Baum von GitHub gekürzt)")
                }
            }

            "read_file" -> {
                val path = a.req("path")
                val (text, _) = gh.readFile(repo, path, branch) ?: return "Datei $path nicht gefunden auf $branch."
                val lines = text.lines()
                val start = (a.int("start_line") ?: 1).coerceIn(1, maxOf(1, lines.size))
                val end = (a.int("end_line") ?: lines.size).coerceIn(start, maxOf(start, lines.size))
                val body = lines.subList(start - 1, minOf(end, lines.size))
                    .mapIndexed { i, l -> "${start + i}\t$l" }
                    .joinToString("\n")
                "$path (Zeilen $start-$end von ${lines.size})\n$body"
            }

            "search_code" -> {
                val q = a.req("query")
                val res = gh.get("search/code", "q" to "$q repo:$repo", "per_page" to "30") as JsonObject
                val items = res["items"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.s("path") }
                if (items.isEmpty()) "Keine Treffer." else "Treffer (${res.s("total_count")}):\n" + items.joinToString("\n")
            }

            "write_file" -> gh.writeFile(repo, a.req("path"), a.req("content"), a.req("commit_message"), branch)

            "edit_file" -> {
                val path = a.req("path")
                val old = a.req("old_text")
                val new = a.s("new_text") ?: ""
                val (text, _) = gh.readFile(repo, path, branch) ?: return "Datei $path nicht gefunden."
                val count = text.windowedCount(old)
                when {
                    count == 0 -> "FEHLER: old_text wurde in $path nicht gefunden. Lies die Datei erneut."
                    count > 1 -> "FEHLER: old_text kommt $count-mal vor. Mehr Kontext angeben."
                    else -> gh.writeFile(repo, path, text.replace(old, new), a.req("commit_message"), branch)
                }
            }

            "delete_file" -> gh.deleteFile(repo, a.req("path"), a.req("commit_message"), branch)

            "list_branches" -> gh.get("repos/$repo/branches", "per_page" to "100")!!.jsonArray
                .mapNotNull { it.jsonObject.s("name") }
                .joinToString("\n") { if (it == branch) "* $it (aktuell)" else it }

            "create_branch" -> {
                val newName = a.req("name")
                val msg = gh.createBranch(repo, newName, a.s("from") ?: branch)
                ctx.branch = newName
                "$msg Arbeitsbranch ist jetzt '$newName'."
            }

            "switch_branch" -> {
                val target = a.req("name")
                gh.get("repos/$repo/branches/$target")
                ctx.branch = target
                "Arbeitsbranch ist jetzt '$target'."
            }

            "list_commits" -> gh.get(
                "repos/$repo/commits", "sha" to branch, "per_page" to (a.int("count") ?: 10).toString(),
            )!!.jsonArray.joinToString("\n") { el ->
                val o = el.jsonObject
                val c = o["commit"] as JsonObject
                val author = (c["author"] as? JsonObject)?.s("name")
                "${o.s("sha")?.take(7)} ${c.s("message")?.lineSequence()?.firstOrNull()} ($author)"
            }

            "create_pull_request" -> {
                val base = a.s("base") ?: gh.defaultBranch(repo)
                if (base == branch) return "FEHLER: Arbeitsbranch und Basisbranch sind identisch ($branch). Erst create_branch nutzen."
                val res = gh.call("POST", gh.url("repos/$repo/pulls"), buildJsonObject {
                    put("title", a.req("title"))
                    put("body", a.s("body") ?: "")
                    put("head", branch)
                    put("base", base)
                }) as JsonObject
                "Pull Request #${res.s("number")} erstellt: ${res.s("html_url")}"
            }

            "list_pull_requests" -> gh.get("repos/$repo/pulls", "state" to (a.s("state") ?: "open"), "per_page" to "30")!!
                .jsonArray.joinToString("\n") { el ->
                    val o = el.jsonObject
                    "#${o.s("number")} ${o.s("title")} [${o.s("state")}] ${(o["head"] as? JsonObject)?.s("ref")} → ${(o["base"] as? JsonObject)?.s("ref")}"
                }.ifEmpty { "Keine Pull Requests." }

            "list_issues" -> gh.get("repos/$repo/issues", "state" to (a.s("state") ?: "open"), "per_page" to "30")!!
                .jsonArray.joinToString("\n") { el ->
                    val o = el.jsonObject
                    val kind = if (o.containsKey("pull_request")) "PR" else "Issue"
                    "#${o.s("number")} [$kind, ${o.s("state")}] ${o.s("title")}"
                }.ifEmpty { "Keine Issues." }

            "read_issue" -> {
                val n = a.int("number") ?: throw IllegalArgumentException("Parameter 'number' fehlt")
                val issue = gh.get("repos/$repo/issues/$n") as JsonObject
                val comments = gh.get("repos/$repo/issues/$n/comments", "per_page" to "50")!!.jsonArray
                buildString {
                    append("#$n ${issue.s("title")} [${issue.s("state")}] von ${(issue["user"] as? JsonObject)?.s("login")}\n\n")
                    append(issue.s("body") ?: "(keine Beschreibung)")
                    comments.forEach { el ->
                        val c = el.jsonObject
                        append("\n\n--- Kommentar von ${(c["user"] as? JsonObject)?.s("login")}:\n${c.s("body")}")
                    }
                }
            }

            "create_issue" -> {
                val res = gh.call("POST", gh.url("repos/$repo/issues"), buildJsonObject {
                    put("title", a.req("title"))
                    put("body", a.s("body") ?: "")
                }) as JsonObject
                "Issue #${res.s("number")} erstellt: ${res.s("html_url")}"
            }

            "comment_issue" -> {
                val n = a.int("number") ?: throw IllegalArgumentException("Parameter 'number' fehlt")
                val res = gh.call("POST", gh.url("repos/$repo/issues/$n/comments"), buildJsonObject {
                    put("body", a.req("body"))
                }) as JsonObject
                "Kommentar erstellt: ${res.s("html_url")}"
            }

            else -> "FEHLER: Unbekanntes Werkzeug '$name'."
        }
    }

    private fun String.windowedCount(needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = indexOf(needle)
        while (idx >= 0) {
            count++
            idx = indexOf(needle, idx + needle.length)
        }
        return count
    }

    companion object {
        private val REPO_URL = Regex("github\\.com[/:]([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)")
        private val REPO_NAME = Regex("^([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)$")
        /** First path segments on github.com that are not user or organization names. */
        private val RESERVED = setOf(
            "settings", "orgs", "apps", "marketplace", "features", "topics", "collections", "sponsors",
            "notifications", "login", "join", "search", "explore", "trending", "pricing", "about", "site", "codespaces",
        )

        /** Extracts owner/name from "owner/name" or a GitHub URL (https://github.com/owner/name/...). */
        fun parseRepo(text: String): String? {
            val t = text.trim()
            val m = REPO_URL.find(t) ?: REPO_NAME.find(t) ?: return null
            if (m.groupValues[1].lowercase() in RESERVED) return null
            val name = m.groupValues[2].removeSuffix(".git").trimEnd('.')
            return if (name.isEmpty()) null else "${m.groupValues[1]}/$name"
        }

        /** Finds the first GitHub repository URL in a chat message. */
        fun findRepoUrl(text: String): String? = REPO_URL.findAll(text).firstNotNullOfOrNull { parseRepo(it.value) }

        fun systemPrompt(ctx: RepoContext) = buildString {
            append("Du hast über Werkzeuge Zugriff auf GitHub und kannst selbstständig in Repositories arbeiten ")
            append("(Dateien lesen, suchen, ändern, committen, Branches und Pull Requests erstellen, Issues bearbeiten).\n")
            if (ctx.repo != null) {
                append("Aktuelles Repository: ${ctx.repo}, Arbeitsbranch: ${ctx.branch}.\n")
            } else {
                append("Es ist noch kein Repository ausgewählt – nutze list_repos und select_repo, wenn der Nutzer ein Repository nennt ")
                append("(auch als GitHub-URL).\n")
            }
            append("Arbeitsweise: Verschaffe dir zuerst mit list_files/read_file einen Überblick, bevor du Dateien änderst. ")
            append("Committe nicht direkt auf den Standardbranch, sondern erstelle für Änderungen einen neuen Branch (create_branch), ")
            append("außer der Nutzer wünscht es ausdrücklich anders. Nutze edit_file für kleine Änderungen. ")
            append("Fasse am Ende kurz zusammen, was du getan hast.")
        }
    }
}
