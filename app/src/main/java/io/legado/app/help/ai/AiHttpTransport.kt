package io.legado.app.help.ai

import io.legado.app.help.http.await
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

internal class AiHttpRequest(
    val url: String,
    private val headers: Map<String, String>,
    val body: String,
    val timeoutSeconds: Int,
    val responseByteLimit: Int
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, true) }
        ?.value

    fun headerEntries(): Set<Map.Entry<String, String>> = headers.entries

    override fun toString(): String = "AiHttpRequest(url=[REDACTED], headers=[REDACTED], bodyBytes=${body.toByteArray().size})"
}

internal class AiHttpResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, true) }
        ?.value

    override fun toString(): String = "AiHttpResponse(code=$code, headers=[REDACTED], bodyBytes=${body.toByteArray().size})"
}

internal interface AiHttpTransport {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

internal class OkHttpAiTransport(
    baseClient: OkHttpClient = OkHttpClient.Builder().build()
) : AiHttpTransport {
    private val client = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        val callClient = client.newBuilder()
            .connectTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val httpRequest = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(JSON_MEDIA_TYPE))
            .apply { request.headerEntries().forEach { header(it.key, it.value) } }
            .build()
        try {
            return callClient.newCall(httpRequest).await().use { response ->
                val body = response.body
                if (body.contentLength() > request.responseByteLimit) {
                    throw AiProviderException(AiProviderError.response_too_large)
                }
                val bytes = body.byteStream().use {
                    AiResponseReader.readLimited(it, request.responseByteLimit)
                }
                AiHttpResponse(
                    response.code,
                    response.headers.toMultimap().mapValues { it.value.joinToString(",") },
                    bytes.toString(Charsets.UTF_8)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AiProviderException) {
            throw error
        } catch (_: InterruptedIOException) {
            throw AiProviderException(AiProviderError.timeout)
        } catch (_: Exception) {
            throw AiProviderException(AiProviderError.network)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal object AiResponseReader {
    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) {
                throw AiProviderException(AiProviderError.response_too_large)
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
