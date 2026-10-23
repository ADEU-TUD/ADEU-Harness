package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Work PCs often reach the internet through a company proxy and inspect HTTPS with the company's own
 * certificate. Java ignores both by default, so the app takes them from Windows: the proxy settings
 * (also automatic configuration scripts) and the certificates trusted by Windows.
 */
object Network {
    fun setup() {
        if (!Paths.windows) return
        System.setProperty("java.net.useSystemProxies", "true")
        runCatching { trustWindowsCertificates() }.onFailure { AppLog.e("Windows-Zertifikate", it) }
    }

    /** Java's own CAs plus the ones of Windows in one trust store that all HTTPS connections use. */
    private fun trustWindowsCertificates() {
        val merged = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        var n = 0
        fun add(store: KeyStore?) {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(store)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.toList() }.forEach { cert: X509Certificate ->
                merged.setCertificateEntry("ca${n++}", cert)
            }
        }
        add(null)
        for (type in listOf("Windows-ROOT", "Windows-ROOT-CURRENTUSER", "Windows-ROOT-LOCALMACHINE")) {
            runCatching { add(KeyStore.getInstance(type).apply { load(null, null) }) }
        }
        val password = "adeu-llm".toCharArray()
        val file = File(Paths.cache, "truststore.p12")
        file.outputStream().use { merged.store(it, password) }
        System.setProperty("javax.net.ssl.trustStore", file.absolutePath)
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12")
        System.setProperty("javax.net.ssl.trustStorePassword", String(password))
        AppLog.i("$n vertrauenswürdige Zertifikate (Java + Windows)")
    }
}
