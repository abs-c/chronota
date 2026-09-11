package app.chronota.data.backup

import java.time.Duration

/** Where a WebDAV backup goes and who it authenticates as. */
data class WebDavTarget(val url: String, val user: String, val password: String) {
    val configured: Boolean get() = url.isNotBlank() && user.isNotBlank()
}

/** Why a transfer failed, in the few shapes the UI has words for. */
enum class WebDavFailure { NOT_CONFIGURED, ADDRESS, AUTH, MISSING, SERVER, NETWORK }

class WebDavException(val failure: WebDavFailure) : Exception(failure.name)

/**
 * The little of WebDAV this app needs: create the folder, put a file, get one back. The three verbs
 * go through [WebDavHttp] because Android's `HttpURLConnection` refuses MKCOL and PROPFIND outright,
 * and the app would rather carry a hundred lines of socket plumbing than a whole HTTP client.
 */
class WebDavClient(private val target: WebDavTarget, private val folder: String = "", private val timeout: Duration = Duration.ofSeconds(20)) {

    private val http = WebDavHttp(timeout)

    /** The folder as a full URL, so callers can name a file inside it. */
    fun folderUrl(): String = listOf(target.url.trimEnd('/'), folder.trim('/')).filter { it.isNotBlank() }.joinToString("/")

    /** The file this client reads and writes. */
    fun fileUrl(name: String): String = folderUrl() + "/" + name.trimStart('/')

    /** Verifies the address and the credentials by asking for the target folder. */
    fun probe() {
        request("PROPFIND", folderUrl(), null, depth = "0")
    }

    /** Creates the folder if it is not there yet. A folder that exists answers 405, which is fine. */
    fun ensureFolder() {
        val parts = folder.split('/').filter { it.isNotBlank() }
        parts.fold(target.url.trimEnd('/')) { parent, part ->
            val next = "$parent/$part"
            request("MKCOL", next, null)
            next
        }
    }

    fun put(name: String, text: String) {
        request("PUT", fileUrl(name), text.toByteArray(Charsets.UTF_8))
    }

    fun get(name: String): String = request("GET", fileUrl(name), null)

    private fun request(method: String, url: String, body: ByteArray?, depth: String? = null): String {
        if (!target.configured) throw WebDavException(WebDavFailure.NOT_CONFIGURED)
        val result = try {
            http.exchange(method, url, target.user, target.password, body, depth)
        } catch (error: IllegalArgumentException) {
            throw WebDavException(WebDavFailure.ADDRESS)
        } catch (error: java.net.URISyntaxException) {
            throw WebDavException(WebDavFailure.ADDRESS)
        } catch (error: java.io.IOException) {
            // The exception class is the only clue a failed backup leaves: keep it in the log.
            android.util.Log.w("chronota", "WebDAV $method $url failed: ${error.javaClass.simpleName}: ${error.message}")
            throw WebDavException(WebDavFailure.NETWORK)
        }
        return when {
            result.status in 200..299 -> result.body
            result.status == 401 || result.status == 403 -> throw WebDavException(WebDavFailure.AUTH)
            result.status == 404 -> throw WebDavException(WebDavFailure.MISSING)
            // MKCOL answers 405 for a folder that already exists, which is the outcome we wanted.
            result.status == 405 && method == "MKCOL" -> ""
            else -> throw WebDavException(WebDavFailure.SERVER)
        }
    }
}
