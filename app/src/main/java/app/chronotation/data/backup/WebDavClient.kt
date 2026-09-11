package app.chronotation.data.backup

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/** Where a WebDAV backup goes and who it authenticates as. */
data class WebDavTarget(val url: String, val user: String, val password: String) {
    val configured: Boolean get() = url.isNotBlank() && user.isNotBlank()
}

/** Why a transfer failed, in the few shapes the UI has words for. */
enum class WebDavFailure { NOT_CONFIGURED, ADDRESS, AUTH, MISSING, SERVER, NETWORK }

class WebDavException(val failure: WebDavFailure) : Exception(failure.name)

/**
 * The little of WebDAV this app needs: create the folder, put a file, get one back. Deliberately built
 * on [HttpURLConnection] rather than a library — a backup is three verbs, and the app ships no
 * networking stack otherwise.
 */
class WebDavClient(private val target: WebDavTarget, private val folder: String = "", private val timeout: Duration = Duration.ofSeconds(20)) {

    /** The folder as a full URL, so callers can name a file inside it. */
    fun folderUrl(): String = listOf(target.url.trimEnd('/'), folder.trim('/')).filter { it.isNotBlank() }.joinToString("/")

    /** The file this client reads and writes. */
    fun fileUrl(name: String): String = folderUrl() + "/" + name.trimStart('/')

    /** Verifies the address and the credentials by asking for the target folder. */
    fun probe() {
        request("PROPFIND", folderUrl(), null, depth = "0") { }
    }

    /** Creates the folder if it is not there yet. A folder that exists answers 405, which is fine. */
    fun ensureFolder() {
        val parts = folder.split('/').filter { it.isNotBlank() }
        parts.fold(target.url.trimEnd('/')) { parent, part ->
            val next = "$parent/$part"
            request("MKCOL", next, null) { }
            next
        }
    }

    fun put(name: String, text: String) {
        request("PUT", fileUrl(name), text.toByteArray(Charsets.UTF_8)) { }
    }

    fun get(name: String): String {
        var body = ""
        request("GET", fileUrl(name), null) { body = it.readBytes().toString(Charsets.UTF_8) }
        return body
    }

    private fun request(method: String, url: String, body: ByteArray?, depth: String? = null, read: (java.io.InputStream) -> Unit) {
        if (!target.configured) throw WebDavException(WebDavFailure.NOT_CONFIGURED)
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            throw WebDavException(WebDavFailure.ADDRESS)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeout.toMillis().toInt()
            connection.readTimeout = timeout.toMillis().toInt()
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", credentials())
            depth?.let { connection.setRequestProperty("Depth", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            when {
                code in 200..299 -> read(connection.inputStream ?: return)
                code == 401 || code == 403 -> throw WebDavException(WebDavFailure.AUTH)
                code == 404 -> throw WebDavException(WebDavFailure.MISSING)
                // MKCOL answers 405 for a folder that already exists, which is the outcome we wanted.
                code == 405 && method == "MKCOL" -> Unit
                else -> throw WebDavException(WebDavFailure.SERVER)
            }
        } catch (error: WebDavException) {
            throw error
        } catch (_: IOException) {
            throw WebDavException(WebDavFailure.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    private fun credentials(): String = "Basic " + Base64.encodeToString("${target.user}:${target.password}".toByteArray(), Base64.NO_WRAP)
}
