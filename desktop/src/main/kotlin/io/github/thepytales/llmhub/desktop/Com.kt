package io.github.thepytales.llmhub.desktop

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.COMLateBindingObject
import com.sun.jna.platform.win32.COM.IDispatch
import com.sun.jna.platform.win32.OaIdl
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.Variant.VARIANT
import com.sun.jna.platform.win32.WTypes
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A thread for COM calls (single-threaded apartment) – Outlook (AP-13) and Office (AP-15) each get their own, so a
 * hanging Outlook does not block an export. Every COM object created during a call is released at its end.
 */
internal class ComThread(name: String) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
            r.run()
        }, name).apply { isDaemon = true }
    }

    /**
     * Runs [block] on the thread; waits at most [timeoutS] seconds ([onTimeout] says what to tell the user). Errors of
     * the block come back as they are.
     */
    fun <T> run(timeoutS: Long, onTimeout: () -> Exception, block: () -> T): T {
        // macOS and Linux (AP-31): no COM – answer at once instead of loading Windows libraries on the thread.
        if (!Os.windows) throw UnsupportedOperationException(io.github.thepytales.llmhub.ui.tr("Outlook und Office über COM gibt es nur unter Windows."))
        val future = executor.submit(Callable {
            val objects = ArrayList<ComObj>()
            ComObj.arena.set(objects)
            try {
                block()
            } finally {
                objects.asReversed().forEach { runCatching { it.release() } }
                ComObj.arena.remove()
            }
        })
        return try {
            future.get(timeoutS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            throw onTimeout()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}

/** A COM object (late binding through JNA): properties and methods by name. */
internal class ComObj : COMLateBindingObject {
    constructor(d: IDispatch) : super(d)

    /** A new object of [progId] (CoCreateInstance; attaches to a running single-instance server such as Outlook). */
    constructor(progId: String) : super(progId, false)

    init {
        arena.get()?.add(this)
    }

    private fun raw(type: Int, name: String, args: Array<out VARIANT>): VARIANT.ByReference {
        val r = VARIANT.ByReference()
        if (args.isEmpty()) oleMethod(type, r, name) else oleMethod(type, r, name, arrayOf(*args))
        return r
    }

    /** A property or method (both flags, like Visual Basic): strings, numbers, booleans, dates or objects. */
    fun get(name: String, vararg args: VARIANT): Any? {
        val r = raw(OleAuto.DISPATCH_METHOD or OleAuto.DISPATCH_PROPERTYGET, name, args)
        return when (val v = r.value) {
            null -> null
            is IDispatch -> ComObj(v)
            is WTypes.BSTR -> v.value.also { OleAuto.INSTANCE.VariantClear(r) }
            is OaIdl.DATE -> v.asJavaDate
            is OaIdl.VARIANT_BOOL -> v.booleanValue()
            is Number -> v.toLong()
            else -> v.toString()
        }
    }

    fun obj(name: String, vararg args: VARIANT): ComObj = get(name, *args) as? ComObj ?: throw COMException("Kein Objekt für „$name“.")
    fun objOrNull(name: String, vararg args: VARIANT): ComObj? = runCatching { get(name, *args) as? ComObj }.getOrNull()
    fun str(name: String): String = runCatching { get(name) as? String }.getOrNull().orEmpty()
    fun num(name: String): Long = runCatching { get(name) as? Long }.getOrNull() ?: 0L
    fun bool(name: String): Boolean = runCatching { get(name) as? Boolean }.getOrNull() ?: false
    fun date(name: String): Long? = runCatching { (get(name) as? Date)?.time }.getOrNull()?.takeIf { it > 0 }

    /** Calls a method without using its result. */
    fun run(name: String, vararg args: VARIANT) {
        raw(OleAuto.DISPATCH_METHOD, name, args)
    }

    fun put(name: String, value: VARIANT) = setProperty(name, value)
    fun put(name: String, value: String) = setProperty(name, value)
    fun put(name: String, value: Boolean) = setProperty(name, value)
    fun put(name: String, value: Int) = setProperty(name, value)
    fun put(name: String, value: Date) = setProperty(name, value)

    companion object {
        /** Objects of the running call on this thread (see [ComThread.run]). */
        val arena = ThreadLocal<ArrayList<ComObj>>()
    }
}

/** Reading COM errors – kept apart from [ComObj], whose class needs Windows' libraries as soon as it is loaded. */
internal object ComErrors {
    /** The HRESULT of a COM error, if it is one (also from the message „…(HRESULT: 800401f3)“ when JNA kept only that). */
    fun hresult(e: Throwable): Int? {
        if (e !is COMException) return null
        return e.hresult?.toInt() ?: Regex("HRESULT: ?(?:0x)?([0-9a-fA-F]{8})").find(e.message.orEmpty())?.groupValues?.get(1)?.toLong(16)?.toInt()
    }
}
