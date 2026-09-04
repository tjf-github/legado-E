package io.legado.app.help.ai

import java.net.InetAddress
import java.net.URI

enum class AiProviderError {
    unsafe_endpoint,
    unsafe_redirect,
    authentication,
    rate_limited,
    server,
    http,
    timeout,
    network,
    response_too_large,
    protocol
}

/** Stable, body-free and credential-free error exposed to the UI layer. */
class AiProviderException(
    val error: AiProviderError,
    val statusCode: Int? = null
) : Exception(messageFor(error, statusCode)) {
    companion object {
        private fun messageFor(error: AiProviderError, statusCode: Int?): String = when (error) {
            AiProviderError.unsafe_endpoint -> "AI service endpoint is not allowed"
            AiProviderError.unsafe_redirect -> "AI service redirect is not allowed"
            AiProviderError.authentication -> "AI service authentication failed"
            AiProviderError.rate_limited -> "AI service rate limited the request"
            AiProviderError.server -> "AI service is temporarily unavailable"
            AiProviderError.http -> "AI service returned HTTP ${statusCode ?: "error"}"
            AiProviderError.timeout -> "AI service request timed out"
            AiProviderError.network -> "AI service network request failed"
            AiProviderError.response_too_large -> "AI service response exceeded the size limit"
            AiProviderError.protocol -> "AI service returned an invalid response"
        }
    }
}

object AiEndpointPolicy {
    private const val CHAT_COMPLETIONS = "/chat/completions"

    fun chatCompletionsUrl(config: AiProviderConfig): String {
        val base = validate(config.serviceUrl, config.allowInsecureLocalHttp)
        val path = base.path.trimEnd('/')
        val endpointPath = when {
            path.endsWith(CHAT_COMPLETIONS) -> path
            path.endsWith("/v1") -> "$path$CHAT_COMPLETIONS"
            path.isEmpty() -> "/v1$CHAT_COMPLETIONS"
            else -> "$path/v1$CHAT_COMPLETIONS"
        }
        return URI(base.scheme, null, base.host, base.port, endpointPath, null, null).toASCIIString()
    }

    fun resolveRedirect(currentUrl: String, location: String, allowInsecureLocalHttp: Boolean): String {
        val resolved = URI(currentUrl).resolve(location)
        return validate(resolved.toASCIIString(), allowInsecureLocalHttp).toASCIIString()
    }

    fun sameOrigin(left: String, right: String): Boolean {
        val a = URI(left)
        val b = URI(right)
        return a.scheme.equals(b.scheme, true) &&
            a.host.equals(b.host, true) && effectivePort(a) == effectivePort(b)
    }

    private fun validate(value: String, allowInsecureLocalHttp: Boolean): URI {
        val uri = try {
            URI(value.trim())
        } catch (_: Exception) {
            throw AiProviderException(AiProviderError.unsafe_endpoint)
        }
        if (!uri.isAbsolute || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null
        ) {
            throw AiProviderException(AiProviderError.unsafe_endpoint)
        }
        val scheme = uri.scheme.lowercase()
        if (scheme == "https") return uri
        if (scheme != "http" || !allowInsecureLocalHttp || !isLiteralPrivateHost(uri.host)) {
            throw AiProviderException(AiProviderError.unsafe_endpoint)
        }
        return uri
    }

    private fun isLiteralPrivateHost(host: String): Boolean {
        if (host.equals("localhost", true)) return true
        val isLiteral = host.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) || ':' in host
        if (!isLiteral) return false
        return try {
            InetAddress.getByName(host).let {
                it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }
}
