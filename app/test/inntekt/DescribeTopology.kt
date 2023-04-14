package inntekt

import inntekt.inntektskomponent.InntektConfig
import inntekt.inntektskomponent.InntektRestClient
import inntekt.popp.PoppConfig
import inntekt.popp.PoppRestClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.kafka.streams.v2.test.StreamsMock
import no.nav.aap.ktor.client.AzureConfig
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class DescribeTopology {

    @Test
    fun `mermaid diagram`() {
        val kafka = StreamsMock().apply {
            val azure = AzureConfig(tokenEndpoint = URL("http://azure.mock"), clientId = "", clientSecret = "")
            val inntekt = InntektRestClient(
                inntektConfig = InntektConfig("", ""),
                azureConfig = azure,
            )
            val popp = PoppRestClient(
                poppConfig = PoppConfig("", ""),
                azureConfig = azure,
            )
            connect(
                topology = topology(inntekt, popp),
                config = StreamsConfig("", ""),
                registry = SimpleMeterRegistry(),
            )
        }

        val mermaid = kafka.visulize().mermaid().generateDiagram()
        File("../docs/topology.mmd").apply { writeText(mermaid) }
    }
}
