package no.nav.aap.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.ProductionExceptionHandler
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("app")
private val secureLog = LoggerFactory.getLogger("secureLog")

interface Kafka : AutoCloseable {
    fun start(topology: Topology, kafkaConfig: KafkaConfig)
    fun <V : Any> createProducer(topic: Topic<String, V>): Producer<String, V>
    fun <V : Any> createConsumer(topic: Topic<String, V>): Consumer<String, V>
    fun <V> getStore(name: String): ReadOnlyKeyValueStore<String, V>
    fun healthy(): Boolean
    fun started(): Boolean
}

class KafkaSetup : Kafka {
    private lateinit var config: KafkaConfig
    private lateinit var streams: KafkaStreams
    private var started: Boolean = false

    override fun start(topology: Topology, kafkaConfig: KafkaConfig) {
        streams = KafkaStreams(topology, kafkaConfig.consumer + kafkaConfig.producer)
        streams.setUncaughtExceptionHandler { err: Throwable ->
            secureLog.error("Uventet feil, logger og leser neste record, ${err.message}")
            StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD
        }
        streams.setStateListener { newState, oldState ->
            log.info("Kafka streams state changed: $oldState -> $newState")
            if (newState == KafkaStreams.State.RUNNING) started = true
        }
        config = kafkaConfig
        streams.start()
    }

    override fun <V> getStore(name: String): ReadOnlyKeyValueStore<String, V> =
        streams.store(StoreQueryParameters.fromNameAndType(name, QueryableStoreTypes.keyValueStore()))

    override fun started() = started
    override fun close() = streams.close()
    override fun healthy(): Boolean = streams.state() in listOf(
        KafkaStreams.State.CREATED,
        KafkaStreams.State.RUNNING,
        KafkaStreams.State.REBALANCING
    )

    override fun <V : Any> createConsumer(topic: Topic<String, V>): Consumer<String, V> =
        KafkaConsumer(
            config.consumer + mapOf(CommonClientConfigs.CLIENT_ID_CONFIG to "client-${topic.name}"),
            topic.keySerde.deserializer(),
            topic.valueSerde.deserializer()
        )

    override fun <V : Any> createProducer(topic: Topic<String, V>): Producer<String, V> =
        KafkaProducer(
            config.producer + mapOf(CommonClientConfigs.CLIENT_ID_CONFIG to "client-${topic.name}"),
            topic.keySerde.serializer(),
            topic.valueSerde.serializer()
        )
}

fun named(named: String): Named = Named.`as`(named)

fun <V> materialized(
    storeName: String,
    topic: Topic<String, V>,
): Materialized<String, V, KeyValueStore<Bytes, ByteArray>> =
    Materialized.`as`<String?, V, KeyValueStore<Bytes, ByteArray>?>(storeName)
        .withKeySerde(topic.keySerde)
        .withValueSerde(topic.valueSerde)

fun <V> ReadOnlyKeyValueStore<String, V>.allValues(): List<V> =
    all().use { it.asSequence().map(KeyValue<String, V>::value).toList() }

class LogContinueErrorHandler : ProductionExceptionHandler {
    override fun configure(configs: MutableMap<String, *>?) {}
    override fun handle(
        record: ProducerRecord<ByteArray, ByteArray>?,
        exception: Exception?
    ): ProductionExceptionHandler.ProductionExceptionHandlerResponse {
        secureLog.error("Feil i streams, logger og leser neste record", exception)
        return ProductionExceptionHandler.ProductionExceptionHandlerResponse.CONTINUE
    }
}

fun <V : Any> KStream<String, V>.logConsumed(): KStream<String, V> = transformValues(
    ValueTransformerWithKeySupplier {
        object : ValueTransformerWithKey<String, V?, V?> {
            private lateinit var context: ProcessorContext

            override fun init(context: ProcessorContext) {
                this.context = context
            }

            override fun transform(readOnlyKey: String, value: V?): V? {
                secureLog.info("consumed [${context.topic()}] K:$readOnlyKey V:$value")
                return value
            }

            override fun close() {}
        }
    }
)