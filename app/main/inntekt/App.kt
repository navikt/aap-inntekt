package inntekt

import inntekt.inntektskomponent.InntektConfig
import inntekt.inntektskomponent.InntektRestClient
import inntekt.inntektskomponent.InntektskomponentRequest
import inntekt.kafka.Topics
import inntekt.model.Inntekt
import inntekt.model.InntekterKafkaDto
import inntekt.model.Response
import inntekt.popp.PoppConfig
import inntekt.popp.PoppRestClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Topology
import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.ktor.client.AzureConfig
import no.nav.aap.ktor.config.loadConfig
import org.slf4j.LoggerFactory
import java.time.YearMonth
import java.util.*

private val secureLog = LoggerFactory.getLogger("secureLog")

data class Config(
    val kafka: StreamsConfig,
    val azure: AzureConfig,
    val inntekt: InntektConfig,
    val popp: PoppConfig
)

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: Streams = KafkaStreams()) {
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
                val status = if (kafka.live()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
            get("/ready") {
                val status = if (kafka.ready()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
        }
    }
}

internal fun topology(inntektRestClient: InntektRestClient, poppRestClient: PoppRestClient): Topology {
    return no.nav.aap.kafka.streams.v2.topology {
        consume(Topics.inntekter)
            .filter { value -> value.response == null }
            .map { inntekter ->
                hentInntekterOgLeggTilResponse(
                    inntekter,
                    inntektRestClient,
                    poppRestClient
                )
            }
            .produce(Topics.inntekter)
    }
}

private fun hentInntekterOgLeggTilResponse(
    inntekter: InntekterKafkaDto,
    inntektRestClient: InntektRestClient,
    poppRestClient: PoppRestClient
): InntekterKafkaDto {
    val callId = UUID.randomUUID().toString()

    val request = InntektskomponentRequest(
        fnr = inntekter.personident,
        fom = inntekter.request.fom,
        tom = inntekter.request.tom,
        filter = "ArbeidsavklaringspengerA-inntekt",
        callId = callId,
    )

    val inntekterFraInntektskomponent = inntektRestClient.hentInntektsliste(request).arbeidsInntektMaaned

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