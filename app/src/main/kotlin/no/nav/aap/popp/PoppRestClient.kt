package no.nav.aap.popp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.AzureConfig
import no.nav.aap.ktor.client.HttpClientAzureAdInterceptor.Companion.azureAD
import org.slf4j.LoggerFactory

private const val POPP_CLIENT_SECONDS_METRICNAME = "popp_client_seconds"
private val sikkerLogg = LoggerFactory.getLogger("secureLog")
private val clientLatencyStats: Summary = Summary.build()
    .name(POPP_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency popp, in seconds")
    .register()

class PoppRestClient(
    private val poppConfig: PoppConfig,
    private val azureConfig: AzureConfig
) {

    fun hentInntekter(
        fnr: String,
        fom: Int,
        tom: Int,
        callId: String
    ): PoppResponse =
        clientLatencyStats.startTimer().use {
            runBlocking {
                httpClient.post("${poppConfig.baseUrl}/inntekt/sumPi") {
                    accept(ContentType.Application.Json)
                    header("Nav-Call-Id", callId)
                    contentType(ContentType.Application.Json)
                    setBody(
                        PoppRequest(
                            fnr = fnr,
                            fomAr = fom,
                            tomAr = tom
                        )
                    )
                }.body()
            }
        }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Auth) { azureAD(azureConfig, poppConfig.scope) }
        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                private var logBody = false
                override fun log(message: String) {
                    when {
                        message == "BODY START" -> logBody = true
                        message == "BODY END" -> logBody = false
                        logBody -> sikkerLogg.debug("respons fra POPP: $message")
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