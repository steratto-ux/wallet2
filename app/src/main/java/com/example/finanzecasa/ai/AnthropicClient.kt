package com.example.finanzecasa.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Modelli tra cui scegliere (nomi dell'API). */
enum class AiModel(val id: String, val label: String) {
    HAIKU("claude-haiku-5-5", "Haiku · veloce ed economico"),
    SONNET("claude-sonnet-5-5", "Sonnet · consigliato"),
    OPUS("claude-opus-5-5", "Opus · il più accurato");

    companion object {
        fun fromId(id: String?): AiModel = values().firstOrNull { it.id == id } ?: SONNET
    }
}

/** Esito di una richiesta: il testo del report oppure un messaggio d'errore leggibile. */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Failure(val message: String) : AiResult()
}

/**
 * Client minimale per l'API Messages di Anthropic (nessuna libreria aggiuntiva).
 * La chiave API è quella personale dell'utente e resta solo sul telefono.
 */
object AnthropicClient {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"
    private const val MAX_TOKENS = 1800

    suspend fun complete(apiKey: String, model: AiModel, system: String, user: String): AiResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model.id)
                .put("max_tokens", MAX_TOKENS)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                .toString()
            val conn = try {
                (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("x-api-key", apiKey.trim())
                    setRequestProperty("anthropic-version", API_VERSION)
                }
            } catch (e: IOException) {
                return@withContext AiResult.Failure("Impossibile contattare il servizio: ${e.message}")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) parseSuccess(text) else AiResult.Failure(explainError(code, text))
            } catch (e: IOException) {
                AiResult.Failure("Connessione non riuscita. Controlla che il telefono sia online e riprova.")
            } catch (e: Exception) {
                AiResult.Failure("Risposta non valida dal servizio: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }

    /** Il testo del report è l'unione dei blocchi di tipo "text" della risposta. */
    internal fun parseSuccess(json: String): AiResult {
        val content = JSONObject(json).optJSONArray("content") ?: return AiResult.Failure("Risposta vuota dal servizio.")
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()
        return if (text.isEmpty()) AiResult.Failure("Il servizio non ha restituito testo.") else AiResult.Success(text)
    }

    private fun explainError(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val base = when (code) {
            401 -> "Chiave API non valida o scaduta. Controllala in Configurazione."
            403 -> "La chiave API non ha il permesso di usare questo modello."
            404 -> "Modello non trovato: scegline un altro in Configurazione."
            429 -> "Troppe richieste o credito esaurito. Riprova tra poco e controlla il credito sul tuo account."
            in 500..599 -> "Il servizio è momentaneamente non disponibile. Riprova tra qualche minuto."
            else -> "Errore $code dal servizio."
        }
        return if (detail.isNotBlank() && code !in setOf(401, 429)) "$base ($detail)" else base
    }
}
