package no.nav.aap

import io.ktor.application.*
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
import no.nav.aap.kafka.*
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology


fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: Kafka = KafkaSetup()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }

    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

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
        .filter { _, inntekter -> inntekter.response == null }
        .mapValues(::addInntekterResponse)
        .to(topics.inntekter, topics.inntekter.produced("produced--inntekter"))
}.build()

// TODO Legg til ekte verdier på sikt
private fun addInntekterResponse(inntekter: Inntekter): Inntekter =
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