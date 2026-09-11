package app.chronota.data.backup

import android.security.NetworkSecurityPolicy
import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.time.Duration
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** The status code and response body, which is all this app looks at. */
class HttpResult(val status: Int, val body: String)

/**
 * The one HTTP/1.1 exchange a WebDAV backup needs, over a plain socket.
 *
 * Android's [java.net.HttpURLConnection] accepts only a fixed list of verbs — OPTIONS, GET, HEAD,
 * POST, PUT, DELETE, TRACE, PATCH — and throws `ProtocolException` for WebDAV's MKCOL and PROPFIND.
 * That failure is an [IOException], so it used to surface as an ordinary "unreachable" error even
 * when the network was fine. Three verbs do not justify bundling an HTTP client, so the request is
 * written by hand instead. TLS still uses the platform trust store with HTTPS endpoint
 * identification, so an untrusted or mismatched certificate fails the handshake as usual.
 *
 * A URL that cannot be parsed, has no host, or asks for cleartext traffic is rejected as an
 * [IllegalArgumentException]; anything that fails while connecting or transferring is an
 * [IOException]. Callers map the two to different messages.
 */
class WebDavHttp(private val timeout: Duration) {

    fun exchange(method: String, url: String, user: String, password: String, body: ByteArray?, depth: String?): HttpResult {
        val uri = URI(url)
        val host = uri.host?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("No host in $url")
        val secure = uri.scheme.equals("https", ignoreCase = true)
        if (!secure && !uri.scheme.equals("http", ignoreCase = true)) throw IllegalArgumentException("Unsupported scheme in $url")
        // Raw sockets bypass the platform's cleartext policy, so ask it explicitly rather than let a
        // Basic-auth password cross the network in the clear.
        if (!secure && !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)) {
            throw IllegalArgumentException("Cleartext traffic to $host is not permitted")
        }
        val port = if (uri.port != -1) uri.port else if (secure) 443 else 80
        val target = buildString {
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
        open(secure, host, port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            write(socket, method, target, host, user, password, body, depth)
            return read(BufferedInputStream(socket.getInputStream()))
        }
    }

    private fun open(secure: Boolean, host: String, port: Int): Socket {
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(host, port), timeout.toMillis().toInt())
            if (!secure) return plain
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
            val parameters = ssl.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            parameters.serverNames = listOf(SNIHostName(host))
            ssl.sslParameters = parameters
            ssl.startHandshake()
            return ssl
        } catch (error: Throwable) {
            // A refused connection or a failed handshake must not leak the socket it happened on.
            plain.close()
            throw error
        }
    }

    private fun write(socket: Socket, method: String, target: String, host: String, user: String, password: String, body: ByteArray?, depth: String?) {
        val credentials = Base64.encodeToString("$user:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val head = buildString {
            append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("Authorization: Basic ").append(credentials).append("\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n")
            depth?.let { append("Depth: ").append(it).append("\r\n") }
            if (body != null) append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(body?.size ?: 0).append("\r\n\r\n")
        }
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        body?.let { out.write(it) }
        out.flush()
    }

    private fun read(input: InputStream): HttpResult {
        val statusLine = readLine(input) ?: throw IOException("Empty response")
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Bad status line: $statusLine")
        var length = -1L
        var chunked = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            when (line.substring(0, colon).trim().lowercase()) {
                "content-length" -> length = line.substring(colon + 1).trim().toLongOrNull() ?: -1L
                "transfer-encoding" -> chunked = line.substring(colon + 1).contains("chunked", ignoreCase = true)
            }
        }
        val bytes = when {
            chunked -> readChunked(input)
            length >= 0 -> readExactly(input, length)
            // Connection: close, so a body without a length ends at end of stream.
            else -> readExactly(input, MAX_BODY)
        }
        return HttpResult(status, bytes.toString(Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        var byte = input.read()
        if (byte == -1) return null
        while (byte != -1) {
            if (byte == '\n'.code) return line.toString()
            if (byte != '\r'.code) line.append(byte.toChar())
            byte = input.read()
        }
        return line.toString()
    }

    private fun readExactly(input: InputStream, length: Long): ByteArray {
        if (length > MAX_BODY) throw IOException("Response too large")
        val out = ByteArrayOutputStream(length.toInt().coerceAtLeast(0))
        val buffer = ByteArray(8192)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val header = readLine(input) ?: break
            val size = header.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                // Consume any trailer lines up to the blank one that closes the message.
                while (true) if (readLine(input)?.isEmpty() != false) break
                break
            }
            if (out.size() + size > MAX_BODY) throw IOException("Response too large")
            val chunk = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val read = input.read(chunk, offset, size - offset)
                if (read < 0) break
                offset += read
            }
            out.write(chunk, 0, offset)
            readLine(input)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BODY = 32L * 1024 * 1024
    }
}
