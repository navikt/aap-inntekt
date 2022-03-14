package no.nav.aap.inntektskomponent

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.aap.azure.AzureClient
import java.time.YearMonth

private const val INNTEKTSKOMPONENT_CLIENT_SECONDS_METRICNAME = "inntektskomponent_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(INNTEKTSKOMPONENT_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency inntektskomponenten, in seconds")
    .register()

class InntektRestClient(
    private val proxyBaseUrl: String,
    private val scope: String,
    private val httpClient: HttpClient,
    private val azureClient: AzureClient
) {
    fun hentInntektsliste(
        fnr: String,
        fom: YearMonth,
        tom: YearMonth,
        filter: String,
        callId: String
    ): InntektskomponentResponse = clientLatencyStats.startTimer().use {
        runBlocking {
            httpClient.request<HttpStatement>("$proxyBaseUrl/api/v1/hentinntektliste") {
                method = HttpMethod.Post
                header("Authorization", "Bearer ${azureClient.getToken(scope)}")
                header("Nav-Call-Id", callId)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                body = mapOf(
                    "ident" to mapOf(
                        "identifikator" to fnr,
                        "aktoerType" to "NATURLIG_IDENT"
                    ),
                    "ainntektsfilter" to filter,
                    "formaal" to "Arbeidsavklaringspenger",
                    "maanedFom" to fom,
                    "maanedTom" to tom
                )
            }.receive()
        }
    }
}

data class InntektskomponentResponse(
    val arbeidsInntektMaaned: List<Måned>
)

data class Måned(
    val årMåned: YearMonth,
    val arbeidsforholdliste: List<Arbeidsforhold>,
    val inntektsliste: List<Inntekt>
)
data class Arbeidsforhold(
    val type: String?,
    val orgnummer: String?
)
data class Inntekt(
    val beløp: Double,
    val inntektstype: Inntektstype,
    val orgnummer: String?,
    val fødselsnummer: String?,
    val aktørId: String?,
    val beskrivelse: String?,
    val fordel: String?
)

enum class Inntektstype {
    LOENNSINNTEKT,
    NAERINGSINNTEKT,
    PENSJON_ELLER_TRYGD,
    YTELSE_FRA_OFFENTLIGE
}