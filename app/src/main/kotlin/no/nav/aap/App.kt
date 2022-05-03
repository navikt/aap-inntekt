package no.nav.aap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.avro.inntekter.v1.Inntekt
import no.nav.aap.avro.inntekter.v1.Inntekter
import no.nav.aap.avro.inntekter.v1.Response
import no.nav.aap.azure.AzureClient
import no.nav.aap.config.Config
import no.nav.aap.config.loadConfig
import no.nav.aap.inntektskomponent.InntektRestClient
import no.nav.aap.kafka.*
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.slf4j.LoggerFactory
import java.time.YearMonth
import java.util.UUID

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

private val secureLog = LoggerFactory.getLogger("secureLog")

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: Kafka = KafkaSetup()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> secureLog.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

    val inntektRestClient = InntektRestClient(
        config.inntekt.proxyBaseUrl,
        config.inntekt.scope,
        simpleHttpClient(),
        AzureClient(config.azure.tokenEndpoint, config.azure.clientId, config.azure.clientSecret)
    )

    val topics = Topics(config.kafka)
    val topology = createTopology(topics, inntektRestClient)
    kafka.start(topology, config.kafka)


    routing {
        get("actuator/healthy") {
            call.respond("ok")
        }

        get("metrics") {
            call.respond(prometheus.scrape())
        }
    }
}

private fun createTopology(topics: Topics, inntektRestClient: InntektRestClient): Topology = StreamsBuilder().apply {
    stream(topics.inntekter.name, topics.inntekter.consumed("inntekter-behov-mottatt"))
        .logConsumed()
        .filter { _, inntekter -> inntekter.response == null }
//        .mapValues { inntekter -> hentInntekterOgLeggTilResponse(inntekter, inntektRestClient) }
        .mapValues { inntekter -> addMockInntekterResponse(inntekter) }
        .to(topics.inntekter, topics.inntekter.produced("produced--inntekter"))
}.build()

private fun hentInntekterOgLeggTilResponse(inntekter: Inntekter, inntektRestClient: InntektRestClient): Inntekter {
    val fomYear = YearMonth.from(inntekter.request.fom)
    val tomYear = YearMonth.from(inntekter.request.tom)

    val inntekterFraInntektskomponent = inntektRestClient.hentInntektsliste(
        inntekter.personident, fomYear, tomYear, "11-19", UUID.randomUUID().toString()
    ).arbeidsInntektMaaned

    return inntekter.apply {
        response = Response.newBuilder()
            .setInntekter(
                inntekterFraInntektskomponent.flatMap { måned ->
                    måned.inntektsliste.map {
                        Inntekt(it.orgnummer, måned.årMåned.atDay(1), it.beløp)
                    }
                }
            )
            .build()
    }
}

private fun addMockInntekterResponse(inntekter: Inntekter): Inntekter =
    inntekter.apply {
        response = Response.newBuilder()
            .setInntekter(
                listOf(
                    Inntekt("321", request.fom.plusYears(2), 400000.0),
                    Inntekt("321", request.fom.plusYears(1), 400000.0),
                    Inntekt("321", request.fom, 400000.0)
                )
            )
            .build()
    }

private fun simpleHttpClient() = HttpClient {
    val sikkerLogg = LoggerFactory.getLogger("secureLog")

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

    install(HttpTimeout) {
        connectTimeoutMillis = 10000
        requestTimeoutMillis = 10000
        socketTimeoutMillis = 10000
    }

    install(ContentNegotiation) {
        jackson { objectMapper }
    }
}