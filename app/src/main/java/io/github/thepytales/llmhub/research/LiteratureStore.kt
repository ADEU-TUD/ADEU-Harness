package io.github.thepytales.llmhub.research

import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.ListStore
import java.io.File

/**
 * The literature collection: works the user or the literature search kept, grouped in named lists. Adding a
 * work that is already there (same DOI or title and year) only adds the list and keeps the richer details.
 */
class LiteratureStore(dir: File) : ListStore<Reference>(FileBackend(File(dir, "literature.json")), Reference.serializer(), { it.id }) {

    /** Names of all lists, most recently used first. */
    fun lists(): List<String> = current.sortedByDescending { it.addedAt }.flatMap { it.lists }.distinct()

    fun inList(name: String): List<Reference> = current.filter { r -> r.lists.any { it.equals(name, ignoreCase = true) } }

    /** Adds [refs] to the collection (and to [list]); returns the stored entries. */
    fun addAll(refs: List<Reference>, list: String? = null, folderId: String? = null): List<Reference> {
        val out = mutableListOf<Reference>()
        refs.forEach { r ->
            val existing = current.firstOrNull { it.dedupeKey == r.dedupeKey && !r.unparsed }
            val stored = if (existing != null) {
                val (merged, _) = Bibliography.merge(existing, r)
                merged.copy(
                    id = existing.id,
                    lists = (existing.lists + listOfNotNull(list?.trim()?.ifBlank { null })).distinct(),
                    note = existing.note.ifBlank { r.note },
                    citations = r.citations ?: existing.citations,
                    openAccessUrl = existing.openAccessUrl.ifBlank { r.openAccessUrl },
                    addedAt = System.currentTimeMillis(),
                    folderId = existing.folderId ?: folderId,
                )
            } else {
                r.copy(lists = (r.lists + listOfNotNull(list?.trim()?.ifBlank { null })).distinct(), folderId = r.folderId ?: folderId, addedAt = System.currentTimeMillis())
            }
            upsert(stored)
            out += stored
        }
        return out
    }

    fun removeFromList(id: String, list: String) = update(id) { r -> r.copy(lists = r.lists.filterNot { it.equals(list, ignoreCase = true) }) }

    fun renameList(old: String, new: String) = replaceAll(current.map { r ->
        if (r.lists.any { it.equals(old, true) }) r.copy(lists = r.lists.map { if (it.equals(old, true)) new.trim() else it }.distinct()) else r
    })
}
