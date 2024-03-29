package inntekt.inntektskomponent

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.AzureAdTokenProvider
import no.nav.aap.ktor.client.AzureConfig
import org.slf4j.LoggerFactory

private const val INNTEKTSKOMPONENT_CLIENT_SECONDS_METRICNAME = "inntektskomponent_client_seconds"
private val sikkerLogg = LoggerFactory.getLogger("secureLog")
private val clientLatencyStats: Summary = Summary.build()
    .name(INNTEKTSKOMPONENT_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency inntektskomponenten, in seconds")
    .register()
private val objectMapper = jacksonObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(JavaTimeModule())

class InntektRestClient(
    private val inntektConfig: InntektConfig,
    azureConfig: AzureConfig
) {
    private val tokenProvider = AzureAdTokenProvider(azureConfig, inntektConfig.scope)

    fun hentInntektsliste(request: InntektskomponentRequest): InntektskomponentResponse =
        clientLatencyStats.startTimer().use {
            runBlocking {
                httpClient.post("${inntektConfig.proxyBaseUrl}/rs/api/v1/hentinntektliste") {
                    accept(ContentType.Application.Json)
                    header("Nav-Call-Id", request.callId)
                    bearerAuth(tokenProvider.getClientCredentialToken())
                    contentType(ContentType.Application.Json)
                    setBody(request.hentinntektliste())
                }
                    .bodyAsText()
                    .also { svar -> sikkerLogg.info("Svar fra inntektskomponenten:\n$svar") }
                    .let(objectMapper::readValue)
            }
        }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                private var logBody = false
                override fun log(message: String) {
                    when {
                        message == "BODY START" -> logBody = true
                        message == "BODY END" -> logBody = false
                        logBody -> sikkerLogg.debug("respons fra Inntektskomponenten: $message")
                    }
                }
            }
        }

        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }
    }
}
