package no.nav.helse.flex

import no.nav.helse.flex.kafka.FLEX_SYKEPENGESOKNAD_TOPIC
import no.nav.syfo.kafka.felles.*
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VarselTest : BaseTestClass() {

    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    @Test
    @Order(1)
    fun `Vi mottar en søknad med status NY`() {
        // mockPdlResponse(expectedCount = manyTimes())
        val soknad = SykepengesoknadDTO(
            fnr = fnr,
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = "999111555")
        )

        kafkaProducer.send(
            ProducerRecord(
                FLEX_SYKEPENGESOKNAD_TOPIC,
                null,
                soknad.id,
                soknad.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 1
        }
    }
}
