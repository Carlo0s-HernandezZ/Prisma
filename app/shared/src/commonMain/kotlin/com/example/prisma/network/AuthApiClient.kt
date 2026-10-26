package com.example.prisma.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class AuthApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = "http://localhost:8080"
    //private val baseUrl = "http://192.168.100.70:8080" // 10.0.2.2 apunta al localhost de la máquina desde el emulador Android o 192.168.100.70 apunta a la ip de mi computadora o localhost

    suspend fun login(email: String, pass: String): LoginResponse {
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, pass))
        }

        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            throw Exception("Usuario o contraseña incorrectos")
        }
    }

    suspend fun verify2Fa(tempToken: String, code: String): Verify2FaResponse {
        val response = client.post("$baseUrl/api/v1/auth/verify-2fa") {
            contentType(ContentType.Application.Json)
            setBody(Verify2FaRequest(tempToken, code))
        }
        return response.body()
    }
}