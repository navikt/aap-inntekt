package no.nav.aap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.metrics.micrometer.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.avro.inntekter.v1.Inntekt
import no.nav.aap.avro.inntekter.v1.Inntekter
import no.nav.aap.avro.inntekter.v1.Response
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

//    val inntektRestClient = InntektRestClient(
//        config.inntekt.proxyBaseUrl,
//        config.inntekt.scope,
//        simpleHttpClient(),
//        AzureClient(config.azure.tokenEndpoint, config.azure.clientId, config.azure.clientSecret)
//    )

    val topics = Topics(config.kafka)
    val topology = createTopology(topics)
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

private fun createTopology(topics: Topics): Topology = StreamsBuilder().apply {
    stream(topics.inntekter.name, topics.inntekter.consumed("inntekter-behov-mottatt"))
        .logConsumed()
        .foreach { _, _ ->  }
//        .filter { _, inntekter -> inntekter.response == null }
//        .mapValues { inntekter -> hentInntekterOgLeggTilResponse(inntekter, inntektRestClient) }
//        .to(topics.inntekter, topics.inntekter.produced("produced--inntekter"))
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

    install(JsonFeature) {
        this.serializer = JacksonSerializer(jackson = objectMapper)
    }
}