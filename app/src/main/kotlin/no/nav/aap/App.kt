package no.nav.aap

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.inntektskomponent.InntektConfig
import no.nav.aap.inntektskomponent.InntektRestClient
import no.nav.aap.kafka.KafkaConfig
import no.nav.aap.kafka.Topics
import no.nav.aap.kafka.streams.*
import no.nav.aap.ktor.client.AzureConfig
import no.nav.aap.ktor.config.loadConfig
import no.nav.aap.model.Inntekt
import no.nav.aap.model.InntekterKafkaDto
import no.nav.aap.model.Response
import no.nav.aap.popp.PoppConfig
import no.nav.aap.popp.PoppRestClient
import org.slf4j.LoggerFactory
import java.time.YearMonth
import java.util.*

private val secureLog = LoggerFactory.getLogger("secureLog")

data class Config(
    val kafka: KafkaConfig,
    val azure: AzureConfig,
    val inntekt: InntektConfig,
    val popp: PoppConfig
)

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: KStreams = KafkaStreams) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> secureLog.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

    val inntektRestClient = InntektRestClient(config.inntekt, config.azure)
    val poppRestClient = PoppRestClient(config.popp, config.azure)

    kafka.start(config.kafka, prometheus) {
        consume(Topics.inntekter)
            .filterNotNull { "filter-innekter-tombstone" }
            .filter { _, value -> value.response == null }
            .mapValues { inntekter -> hentInntekterOgLeggTilResponse(inntekter, inntektRestClient, poppRestClient) }
            .produce(Topics.inntekter) { "produced-inntekter-med-response" }
    }

    routing {
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                val status = if (kafka.isLive()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
            get("/ready") {
                val status = if (kafka.isReady()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
        }
    }
}

private fun hentInntekterOgLeggTilResponse(
    inntekter: InntekterKafkaDto,
    inntektRestClient: InntektRestClient,
    poppRestClient: PoppRestClient
): InntekterKafkaDto {
    val callId = UUID.randomUUID().toString()
    val inntekterFraInntektskomponent = inntektRestClient.hentInntektsliste(
        inntekter.personident,
        inntekter.request.fom,
        inntekter.request.tom,
        "11-19",
        callId
    ).arbeidsInntektMaaned

    val inntekterFraPopp = poppRestClient.hentInntekter(
        inntekter.personident,
        inntekter.request.fom.year,
        inntekter.request.tom.year,
        callId
    )

    return inntekter.copy(
        response = Response(
            inntekter = inntekterFraInntektskomponent.flatMap { måned ->
                måned.inntektsliste.map {
                    Inntekt(it.orgnummer ?: "ukjent", måned.årMåned, it.beløp)
                } + inntekterFraPopp.inntekter.map {
                    Inntekt("ukjent", YearMonth.of(it.inntektAr, 1), it.belop)
                }
            }
        )
    )
}