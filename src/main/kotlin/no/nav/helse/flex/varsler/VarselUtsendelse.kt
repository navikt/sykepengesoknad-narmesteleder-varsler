package no.nav.helse.flex.varsler

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.*
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class VarselUtsendelse(
    private val planlagtVarselRepository: PlanlagtVarselRepository,
    private val narmesteLederRepository: NarmesteLederRepository,
    private val kafkaProducer: Producer<String, NotifikasjonMedkontaktInfo>,
    private val registry: MeterRegistry
) {

    val log = logger()

    fun sendVarsler(now: Instant = Instant.now()): Int {
        val planlagteVarsler =
            planlagtVarselRepository.findFirst300ByStatusAndSendesIsBefore(PLANLAGT, now)
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
                log.info("Fant ingen nærmeste leder for planlagt varsel ${planlagtVarsel.id} for søknad ${planlagtVarsel.sykepengesoknadId}")
                planlagtVarselRepository.save(planlagtVarsel.copy(oppdatert = Instant.now(), status = INGEN_LEDER))
                lagreMetrikk(INGEN_LEDER, planlagtVarsel.varselType)
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
            log.info("Sendt planlagt varsel ${planlagtVarsel.id} for søknad ${planlagtVarsel.sykepengesoknadId} med type ${planlagtVarsel.varselType} til ${narmesteLeder.narmesteLederId}")

            planlagtVarselRepository.save(
                planlagtVarsel.copy(
                    oppdatert = Instant.now(),
                    status = SENDT,
                    narmesteLederId = narmesteLeder.narmesteLederId
                )
            )
            lagreMetrikk(SENDT, planlagtVarsel.varselType)
            varslerSendt++
        }
        return varslerSendt
    }

    private fun lagreMetrikk(status: PlanlagtVarselStatus, type: PlanlagtVarselType) {
        registry.counter(
            "planlagt_varsel_behandlet",
            Tags.of(
                "status", status.name,
                "type", type.name,
            )
        ).increment()
    }
}
