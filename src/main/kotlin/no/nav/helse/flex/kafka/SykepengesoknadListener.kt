package no.nav.helse.flex.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.brukeroppgave.BrukeroppgaveOpprettelse
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.varsler.VarselPlanlegger
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SykepengesoknadListener(
    private val brukeroppgaveOpprettelse: BrukeroppgaveOpprettelse,
    private val varselPlanlegger: VarselPlanlegger
) {

    private val log = logger()

    @KafkaListener(
        topics = [FLEX_SYKEPENGESOKNAD_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            val soknad = cr.value().tilSykepengesoknadDTO()
            brukeroppgaveOpprettelse.opprettBrukeroppgave(soknad)
            varselPlanlegger.planleggVarsler(soknad, Instant.ofEpochMilli(cr.timestamp()))
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}", e)
            throw e
        }
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}
const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"
