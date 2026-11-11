package com.example.prisma.profile

import com.example.prisma.api.PersonalData
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

enum class ProfileError { Unauthorized, NotFound, Invalid, Network, Unknown }

/** [serverMessage]: explicación del servidor (p. ej. "La fecha de nacimiento no puede ser futura."). */
class ProfileException(val error: ProfileError, val serverMessage: String? = null) : Exception(error.name)

interface ProfileRepository {
    /** Devuelve null si el usuario todavía no tiene datos personales. */
    suspend fun load(): Result<PersonalData?>
    suspend fun save(data: PersonalData): Result<Unit>
    suspend fun delete(): Result<Unit>
}

class KtorProfileRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : ProfileRepository {
    private val url get() = "$baseUrl/api/v1/user/profile"

    override suspend fun load(): Result<PersonalData?> = request(
        send = { client.get(url) { withToken(accessToken) } },
        onNotFound = { null },
    ) { it.body<PersonalData>() }

    override suspend fun save(data: PersonalData): Result<Unit> = request(
        send = {
            client.post(url) {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(data)
            }
        },
    ) { }

    override suspend fun delete(): Result<Unit> = request(
        send = { client.delete(url) { withToken(accessToken) } },
    ) { }

    private suspend fun <T> request(
        send: suspend () -> HttpResponse,
        onNotFound: (() -> T)? = null,
        parse: suspend (HttpResponse) -> T,
    ): Result<T> = try {
        val response = send()
        when {
            response.status.isSuccess() -> Result.success(parse(response))
            response.status == HttpStatusCode.NotFound && onNotFound != null -> Result.success(onNotFound())
            else -> Result.failure(
                ProfileException(
                    when (response.status) {
                        HttpStatusCode.Unauthorized -> ProfileError.Unauthorized
                        HttpStatusCode.NotFound -> ProfileError.NotFound
                        HttpStatusCode.BadRequest -> ProfileError.Invalid
                        else -> ProfileError.Unknown
                    },
                    serverMessage = response.apiErrorMessage(),
                )
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) { // En web (wasm) un fallo de red llega como kotlin.Error, no Exception.
        Result.failure(ProfileException(ProfileError.Network))
    }
}
