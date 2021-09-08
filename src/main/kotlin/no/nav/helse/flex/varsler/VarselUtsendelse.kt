package no.nav.helse.flex.varsler

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.*
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

@Component
class VarselUtsendelse(
    private val planlagtVarselRepository: PlanlagtVarselRepository,
    private val narmesteLederRepository: NarmesteLederRepository,
    private val kafkaProducer: Producer<String, NotifikasjonMedkontaktInfo>
) {

    val log = logger()

    fun sendVarsler(now: OffsetDateTime = OffsetDateTime.now()): Int {
        val planlagteVarsler =
            planlagtVarselRepository.findFirst100ByStatusAndSendesIsBefore(PLANLAGT, now)
        var varslerSendt = 0

        log.info("Fant ${planlagteVarsler.size} planlagte varsler som skal sendes før $now")

        planlagteVarsler.forEach { pv ->
            val planlagtVarsel = planlagtVarselRepository.findByIdOrNull(pv.id!!)!!
            if (planlagtVarsel.status != PLANLAGT) {
                log.warn("Planlagt varsel ${planlagtVarsel.id} er ikke lengre planlagt")
                return@forEach
            }
            val narmesteLedere =
                narmesteLederRepository.findByBrukerFnrAndOrgnummer(planlagtVarsel.brukerFnr, planlagtVarsel.orgnummer)

            if (narmesteLedere.isEmpty()) {
                planlagtVarselRepository.save(planlagtVarsel.copy(oppdatert = Instant.now(), status = INGEN_LEDER))
                return@forEach
            }
            if (narmesteLedere.size != 1) {
                log.error("Fant flere nærmeste ledere for planlagt varsel ${planlagtVarsel.id}, nærmeste leder ider ${narmesteLedere.map { it.narmesteLederId }}")
                throw RuntimeException("Flere nærmeste ledere")
            }

            val narmesteLeder = narmesteLedere.first()
            val notifikasjonMedKontaktInfo = when (planlagtVarsel.varselType) {
                PlanlagtVarselType.SENDT_SYKEPENGESOKNAD -> skapSendtSøknadVarsel(planlagtVarsel.id!!, narmesteLeder)
                PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD -> skapManglendeSøknadVarsel(
                    planlagtVarsel.id!!,
                    narmesteLeder
                )
            }
            kafkaProducer.send(
                ProducerRecord(
                    doknotifikasjonTopic,
                    null,
                    notifikasjonMedKontaktInfo.getBestillingsId(),
                    notifikasjonMedKontaktInfo
                )
            )
            planlagtVarselRepository.save(
                planlagtVarsel.copy(
                    oppdatert = Instant.now(),
                    status = SENDT,
                    narmesteLederId = narmesteLeder.narmesteLederId
                )
            )

            varslerSendt++
        }
        return varslerSendt
    }
}
