package br.com.sofapad

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Lê a identidade (SHA-256 do certificado) que o PC apresenta, sem enviar nada secreto:
 * faz só o aperto de mão TLS e fecha. Quem decide se a identidade é a do PC certo é a pessoa,
 * comparando o código de conferência com o da janela do PC, antes de digitar o código de uso único.
 */
object IdentityProbe {
    /** Parte da identidade comparada a olho: 16 caracteres (64 bits) em grupos de quatro. */
    fun checkCode(fingerprint: String): String = fingerprint.take(16).uppercase().chunked(4).joinToString(" ")

    // Executado no dispatcher IO, nunca na thread de interface.
    fun fetch(host: String, port: Int, timeoutMillis: Int = 4_000): String {
        // Reaproveita a validação de endereço local do restante do app.
        ConnectionSettings(host, port, "0".repeat(64), "0".repeat(64))
        var seen: String? = null
        val capture = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                seen = chain.firstOrNull()?.let { cert ->
                    MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
                }
            }
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }
        Socket().use { raw ->
            raw.connect(InetSocketAddress(host, port), timeoutMillis)
            raw.soTimeout = timeoutMillis
            (context.socketFactory.createSocket(raw, "sofapad.local", port, true) as SSLSocket).use { it.startHandshake() }
        }
        return seen?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: throw IOException("Identidade ausente")
    }
}
