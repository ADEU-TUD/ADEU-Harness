package io.github.thepytales.llmhub.data

/**
 * Conversations are trees: every message points to its parent, and messages sharing a parent are
 * alternative versions (an edited question or a regenerated answer). [Conversation.selected] stores
 * which version is shown; the active thread follows those choices from the first message.
 */

/** Key in [Conversation.selected] for messages without a parent. */
const val ROOT_KEY = ""

/** Selection value that ends the active thread at a message (a new version is about to start there). */
const val NO_CHILD = "-"

val ChatMessage.parentKey: String get() = parentId ?: ROOT_KEY

/** The messages of the currently selected versions, in order. */
fun Conversation.thread(): List<ChatMessage> {
    if (messages.isEmpty()) return emptyList()
    val children = messages.groupBy { it.parentKey }
    val out = ArrayList<ChatMessage>()
    val seen = HashSet<String>()
    var key = ROOT_KEY
    while (true) {
        val choice = selected[key]
        if (choice == NO_CHILD) break
        val kids = children[key] ?: break
        val next = kids.firstOrNull { it.id == choice } ?: kids.last()
        if (!seen.add(next.id)) break
        out += next
        key = next.id
    }
    return out
}

/** All versions of [m] (including itself) in creation order. */
fun Conversation.versionsOf(m: ChatMessage): List<ChatMessage> = messages.filter { it.parentKey == m.parentKey }

/** Appends [m] at the end of the active thread. */
fun Conversation.appendToThread(m: ChatMessage): Conversation {
    val msg = m.copy(parentId = thread().lastOrNull()?.id)
    return copy(messages = messages + msg, selected = selected + (msg.parentKey to msg.id))
}

/** Adds [m] as a new version next to [of] and shows it. */
fun Conversation.addVersion(of: ChatMessage, m: ChatMessage): Conversation {
    val msg = m.copy(parentId = of.parentId)
    return copy(messages = messages + msg, selected = selected + (msg.parentKey to msg.id))
}

/** Shows version [m] (and the thread below it). */
fun Conversation.select(m: ChatMessage): Conversation = copy(selected = selected + (m.parentKey to m.id))

/** Ends the active thread after [m], so the next appended message becomes a new version below it. */
fun Conversation.branchAfter(m: ChatMessage): Conversation = copy(selected = selected + (m.id to NO_CHILD))

/** Removes the message [id] and everything below it. */
fun Conversation.removeSubtree(id: String): Conversation {
    val children = messages.groupBy { it.parentKey }
    val drop = HashSet<String>()
    val stack = ArrayDeque<String>()
    stack.addLast(id)
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        if (drop.add(cur)) children[cur]?.forEach { stack.addLast(it.id) }
    }
    return copy(
        messages = messages.filterNot { it.id in drop },
        selected = selected.filter { (k, v) -> k !in drop && v !in drop },
    )
}

/** Chats from older app versions were plain lists: link every message to its predecessor. */
fun Conversation.normalized(): Conversation =
    if (messages.size > 1 && messages.all { it.parentId == null }) {
        copy(messages = messages.mapIndexed { i, m -> if (i == 0) m else m.copy(parentId = messages[i - 1].id) })
    } else this

/** Removes [id] (and everything below it) and lets the thread end at its parent. */
fun Conversation.dropFromThread(id: String): Conversation {
    val m = messages.firstOrNull { it.id == id } ?: return this
    val removed = removeSubtree(id)
    return removed.copy(selected = removed.selected + (m.parentKey to NO_CHILD))
}

/** Changes one message wherever it is in the tree. */
fun Conversation.updateMessage(id: String, transform: (ChatMessage) -> ChatMessage): Conversation =
    copy(messages = messages.map { if (it.id == id) transform(it) else it })
