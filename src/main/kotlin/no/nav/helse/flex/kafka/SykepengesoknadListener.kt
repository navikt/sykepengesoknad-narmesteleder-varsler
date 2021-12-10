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
        val soknad = cr.value().tilSykepengesoknadDTO()
        brukeroppgaveOpprettelse.opprettBrukeroppgave(soknad)
        varselPlanlegger.planleggVarsler(soknad)
        acknowledgment.acknowledge()
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}

const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"
