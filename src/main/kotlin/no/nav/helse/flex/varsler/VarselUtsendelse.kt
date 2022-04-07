package no.nav.helse.flex.varsler

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.dinesykmeldte.DineSykmeldteHendelse
import no.nav.helse.flex.dinesykmeldte.FerdigstillHendelse
import no.nav.helse.flex.dinesykmeldte.OPPGAVETYPE_IKKE_SENDT_SOKNAD
import no.nav.helse.flex.dinesykmeldte.OpprettHendelse
import no.nav.helse.flex.kafka.dineSykmeldteHendelserTopic
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.INGEN_LEDER
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.SENDT
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
    private val doknotifikasjonKafkaProducer: Producer<String, NotifikasjonMedkontaktInfo>,
    private val dineSykmeldteHendelserProducer: Producer<String, String>,
    private val registry: MeterRegistry
) {

    val log = logger()

    fun sendVarsler(now: Instant = Instant.now()): Int {
        val planlagteVarsler = planlagtVarselRepository.findFirst300ByStatusAndSendesIsBefore(PLANLAGT, now)
        var varslerSendt = 0

        log.info("Fant ${planlagteVarsler.size} planlagte varsler som skal sendes før $now.")

        planlagteVarsler.forEach { planlagtVarsel ->
            val lestVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
            if (lestVarsel.status != PLANLAGT) {
                log.warn(
                    "Planlagt varsel ${lestVarsel.id} er ikke lengre planlagt. Det kan skje hvis bruker " +
                        "sender inn søknaden mellom varsel planlegges og faktisk sendes."
                )
                return@forEach
            }
            val narmesteLedere =
                narmesteLederRepository.findByBrukerFnrAndOrgnummer(lestVarsel.brukerFnr, lestVarsel.orgnummer)

            if (narmesteLedere.isEmpty()) {
                log.info(
                    "Fant ingen nærmeste leder for planlagt varsel ${lestVarsel.id} for " +
                        "søknad ${lestVarsel.sykepengesoknadId}."
                )
                planlagtVarselRepository.save(lestVarsel.copy(oppdatert = Instant.now(), status = INGEN_LEDER))
                lagreMetrikk(INGEN_LEDER, lestVarsel.varselType)
                return@forEach
            }
            if (narmesteLedere.size != 1) {
                throw RuntimeException(
                    "Fant flere nærmeste ledere for planlagt varsel ${lestVarsel.id}:" +
                        "${narmesteLedere.map { it.narmesteLederId }}."
                )
            }

            // Sender e-post til nærmeste leder om at det finnes sykepengesøknader som ikke er sendt inn.
            val narmesteLeder = narmesteLedere.first()
            sendNotifikasjonTilNarmesteLeder(lestVarsel, narmesteLeder)

            // Oppretter oppgave i Dine Sykmeldte som gjør at ikke sendte søknader kan identifiseres.
            if (lestVarsel.varselType == PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD) {
                sendOpprettHendelseTilDineSykmeldte(lestVarsel, narmesteLeder)
            }

            lagreMetrikk(SENDT, lestVarsel.varselType)
            varslerSendt++
        }
        return varslerSendt
    }

    fun sendFerdigstillHendelseTilDineSykmeldte(planlagtVarsel: PlanlagtVarsel) {
        val now = OffsetDateTime.now()
        val ferdigstillHendelse = lagFerdigstillHendelse(planlagtVarsel.sykepengesoknadId, now)

        dineSykmeldteHendelserProducer.send(
            ProducerRecord(
                dineSykmeldteHendelserTopic,
                planlagtVarsel.sykepengesoknadId,
                ferdigstillHendelse.serialisertTilString()
            )
        )

        log.info(
            "Sendt ferdigstill hendelse for planlagt varsel ${planlagtVarsel.id} og " +
                "søknad ${planlagtVarsel.sykepengesoknadId} til Dine sykmeldte."
        )

        planlagtVarselRepository.save(
            planlagtVarsel.copy(
                oppdatert = now.toInstant(),
                dineSykmeldteHendelseFerdigstilt = now.toInstant()
            )
        )
    }

    private fun sendNotifikasjonTilNarmesteLeder(
        planlagtVarsel: PlanlagtVarsel,
        narmesteLeder: NarmesteLeder,
    ) {
        val notifikasjon = lagNotifikasjonMedkontaktInfo(planlagtVarsel, narmesteLeder)
        doknotifikasjonKafkaProducer.send(
            ProducerRecord(
                doknotifikasjonTopic,
                null,
                notifikasjon.getBestillingsId(),
                notifikasjon
            )
        )

        log.info(
            "Sendt planlagt varsel ${planlagtVarsel.id} med type ${planlagtVarsel.varselType} for " +
                "søknad ${planlagtVarsel.sykepengesoknadId} til nærmeste leder ${narmesteLeder.narmesteLederId}."
        )

        planlagtVarselRepository.save(
            planlagtVarsel.copy(
                oppdatert = Instant.now(),
                status = SENDT,
                narmesteLederId = narmesteLeder.narmesteLederId
            )
        )
    }

    private fun lagNotifikasjonMedkontaktInfo(
        planlagtVarsel: PlanlagtVarsel,
        narmesteLeder: NarmesteLeder
    ): NotifikasjonMedkontaktInfo {
        val notifikasjon = when (planlagtVarsel.varselType) {
            PlanlagtVarselType.SENDT_SYKEPENGESOKNAD -> skapSendtSøknadVarsel(planlagtVarsel.id!!, narmesteLeder)
            PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD -> skapManglendeSøknadVarsel(
                planlagtVarsel.id!!,
                narmesteLeder
            )
        }
        return notifikasjon
    }

    private fun sendOpprettHendelseTilDineSykmeldte(planlagtVarsel: PlanlagtVarsel, narmesteLeder: NarmesteLeder) {
        val now = OffsetDateTime.now()

        val opprettHendelse =
            lagOpprettHendelseForIkkeSendtSoknad(planlagtVarsel.sykepengesoknadId, planlagtVarsel, now)

        dineSykmeldteHendelserProducer.send(
            ProducerRecord(
                dineSykmeldteHendelserTopic,
                planlagtVarsel.sykepengesoknadId,
                opprettHendelse.serialisertTilString()
            )
        )

        log.info(
            "Sendt opprett hendelse med type ${opprettHendelse.opprettHendelse?.oppgavetype} for planlagt " +
                "varsel ${planlagtVarsel.id} og søknad ${planlagtVarsel.sykepengesoknadId} til Dine sykmeldte."
        )

        planlagtVarselRepository.save(
            planlagtVarsel.copy(
                oppdatert = now.toInstant(),
                status = SENDT,
                narmesteLederId = narmesteLeder.narmesteLederId,
                dineSykmeldteHendelseOpprettet = now.toInstant()
            )
        )
    }

    private fun lagOpprettHendelseForIkkeSendtSoknad(
        id: String,
        planlagtVarsel: PlanlagtVarsel,
        timestamp: OffsetDateTime
    ): DineSykmeldteHendelse {
        return DineSykmeldteHendelse(
            id,
            OpprettHendelse(
                ansattFnr = planlagtVarsel.brukerFnr,
                orgnummer = planlagtVarsel.orgnummer,
                oppgavetype = OPPGAVETYPE_IKKE_SENDT_SOKNAD,
                timestamp = timestamp
            )
        )
    }

    private fun lagFerdigstillHendelse(id: String, timestamp: OffsetDateTime): DineSykmeldteHendelse {
        return DineSykmeldteHendelse(id, null, FerdigstillHendelse(timestamp))
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
