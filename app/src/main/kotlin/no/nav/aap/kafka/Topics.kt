package no.nav.aap.kafka

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import no.nav.aap.avro.inntekter.v1.Inntekter
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced

data class Topic<K, V>(
    val name: String,
    val keySerde: Serde<K>,
    val valueSerde: Serde<V>,
){
    fun consumed(named: String): Consumed<K, V> = Consumed.with(keySerde, valueSerde).withName(named)
    fun produced(named: String): Produced<K, V> = Produced.with(keySerde, valueSerde).withName(named)
    fun <R : Any> joined(right: Topic<K, R>): Joined<K, V, R> =
        Joined.with(keySerde, valueSerde, right.valueSerde, "$name-joined-${right.name}")
}

class Topics(private val config: KafkaConfig) {
    val inntekter = Topic("aap.inntekter.v1", Serdes.StringSerde(), Serdes.ByteArray())

    private fun <T : SpecificRecord> avroSerde(): SpecificAvroSerde<T> = SpecificAvroSerde<T>().apply {
        val avroProperties = config.schemaRegistry + config.ssl
        val avroConfig = avroProperties.map { it.key.toString() to it.value.toString() }
        configure(avroConfig.toMap(), false)
    }
}