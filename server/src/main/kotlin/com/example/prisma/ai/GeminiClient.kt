
package com.example.prisma.ai

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GeminiClient {

    private val apiKey = System.getenv("GEMINI_API_KEY")
        ?.takeIf { it.isNotBlank() }
        ?: error("GEMINI_API_KEY no está configurada")

    private val model = "gemini-3.5-flash-lite"

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    suspend fun generarRespuesta(
        prompt: String,
        formatoJson: Boolean = false
    ): String =
        withContext(Dispatchers.IO) {

            require(prompt.isNotBlank()) {
                "El prompt no puede estar vacío"
            }


            val contenido = buildJsonObject {

                put("contents", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("parts", kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject {
                                put("text", prompt)
                            })
                        })
                    })
                })

                if (formatoJson) {
                    put("generationConfig", buildJsonObject {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.2)
                    })
                }
            }


            val request = HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "https://generativelanguage.googleapis.com/" +
                                "v1beta/models/$model:generateContent"
                    )
                )
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        contenido.toString()
                    )
                )
                .build()

            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            )

            if (response.statusCode() !in 200..299) {
                error(
                    "Gemini no pudo procesar la solicitud. " +
                            "HTTP ${response.statusCode()}"
                )
            }

            val json = Json.parseToJsonElement(response.body())
                .jsonObject

            json["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.mapNotNull { part ->
                    part.jsonObject["text"]
                        ?.jsonPrimitive
                        ?.content
                }
                ?.joinToString("\n")
                ?.takeIf { it.isNotBlank() }
                ?: error("Gemini no devolvió texto")
        }
}
