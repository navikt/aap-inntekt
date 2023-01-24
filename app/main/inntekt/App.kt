package inntekt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import inntekt.inntektskomponent.InntektConfig
import inntekt.inntektskomponent.InntektRestClient
import inntekt.kafka.Topics
import no.nav.aap.kafka.streams.KStreams
import no.nav.aap.kafka.streams.KStreamsConfig
import no.nav.aap.kafka.streams.KafkaStreams
import no.nav.aap.kafka.streams.extension.*
import no.nav.aap.ktor.client.AzureConfig
import no.nav.aap.ktor.config.loadConfig
import inntekt.model.Inntekt
import inntekt.model.InntekterKafkaDto
import inntekt.model.Response
import inntekt.popp.PoppConfig
import inntekt.popp.PoppRestClient
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.slf4j.LoggerFactory
import java.time.YearMonth
import java.util.*

private val secureLog = LoggerFactory.getLogger("secureLog")

data class Config(
    val kafka: KStreamsConfig,
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

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology(inntektRestClient, poppRestClient)
    )

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

private fun topology(inntektRestClient: InntektRestClient, poppRestClient: PoppRestClient): Topology {
    val streams = StreamsBuilder()

    streams.consume(Topics.inntekter)
        .filterNotNull("filter-inntekter-tombstone")
        .filter("filter-inntekt-request") { _, value -> value.response == null }
        .mapValues("lag-inntekter-response") { inntekter ->
            hentInntekterOgLeggTilResponse(
                inntekter,
                inntektRestClient,
                poppRestClient
            )
        }
        .produce(Topics.inntekter, "produced-inntekter-med-response")

    return streams.build()
}

private fun hentInntekterOgLeggTilResponse(
    inntekter: InntekterKafkaDto,
    inntektRestClient: InntektRestClient,
    poppRestClient: PoppRestClient
): InntekterKafkaDto {
    val callId = UUID.randomUUID().toString()

    val inntekterFraInntektskomponent =
        inntektRestClient.hentInntektsliste(
            inntekter.personident,
            inntekter.request.fom,
            inntekter.request.tom,
            "ArbeidsavklaringspengerA-inntekt",
            callId
        ).arbeidsInntektMaaned

    val inntekterFraPopp =
        poppRestClient.hentInntekter(
            inntekter.personident,
            inntekter.request.fom.year,
            inntekter.request.tom.year,
            callId
        )

    return inntekter.copy(
        response = Response(
            inntekter = inntekterFraInntektskomponent.flatMap { måned ->
                måned.arbeidsInntektInformasjon.inntektListe.map {
                    Inntekt("ukjent", måned.aarMaaned, it.beloep)
                }
            } + inntekterFraPopp.inntekter.map {
                Inntekt("ukjent", YearMonth.of(it.inntektAr, 1), it.belop)
            }
        )
    )
}