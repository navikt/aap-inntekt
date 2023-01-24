package no.nav.inntekt.kafka

import no.nav.aap.kafka.serde.json.JsonSerde
import no.nav.aap.kafka.streams.Topic
import no.nav.inntekt.model.InntekterKafkaDto

object Topics {
    val inntekter = Topic("aap.inntekter.v1", JsonSerde.jackson<InntekterKafkaDto>())
}
