package no.nav.helse.flex.varsler

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.*
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import org.springframework.stereotype.Component
import java.time.*

@Component
class VarselPlanlegger(
    private val planlagtVarselRepository: PlanlagtVarselRepository,
    private val varselUtsendelse: VarselUtsendelse,
) {

    val log = logger()

    fun planleggVarsler(soknad: SykepengesoknadDTO) {

        if (!soknad.skalSendeVarselTilArbeidsgiver()) {
            return
        }

        if (soknad.status == NY) {
            soknad.planleggVarselForStatusNy()
        }
        if (soknad.bleSendtTilArbeidsgiver()) {
            soknad.planleggVarselForStatusSendt()
        }
        if (listOf(SENDT, AVBRUTT, SLETTET, KORRIGERT, UTGAATT).contains(soknad.status)) {
            soknad.avbrytManglendeSoknadVarsler()
            soknad.ferdigstillDineSykmeldteHendelse()
        }
    }

    private fun SykepengesoknadDTO.avbrytManglendeSoknadVarsler() {
        planlagtVarselRepository.findBySykepengesoknadId(id)
            .filter { it.varselType == MANGLENDE_SYKEPENGESOKNAD }
            .forEach {
                if (it.status == PLANLAGT) {
                    log.info("Avbryter planlagt varsel med id $id og type $type.")
                    planlagtVarselRepository.save(
                        it.copy(
                            status = PlanlagtVarselStatus.AVBRUTT,
                            oppdatert = Instant.now(),
                        )
                    )
                }
            }
    }

    fun SykepengesoknadDTO.ferdigstillDineSykmeldteHendelse() {
        planlagtVarselRepository.findMedSendtDineSykmeldteHendelse(id, MANGLENDE_SYKEPENGESOKNAD.toString())
            .filter { it.dineSykmeldteHendelseOpprettet != null }
            .forEach {
                varselUtsendelse.sendFerdigstillHendelseTilDineSykmeldte(it)
            }
    }

    private fun SykepengesoknadDTO.planleggVarselForStatusNy() {

        val harAlleredePlanlagt = planlagtVarselRepository.findBySykepengesoknadId(id)
            .any { it.varselType == MANGLENDE_SYKEPENGESOKNAD }

        if (harAlleredePlanlagt) {
            // Dette skjer ved gjenåpning av avbrutt søknad
            log.info("Har allerede planlagt varsel for status NY for soknad $id.")
            return
        }

        val planlagtVarsel = PlanlagtVarsel(
            id = null,
            sykepengesoknadId = id,
            brukerFnr = fnr,
            oppdatert = Instant.now(),
            orgnummer = arbeidsgiver!!.orgnummer!!,
            sendes = omToUkerFornuftigDagtid().toInstant(),
            status = PLANLAGT,
            varselType = MANGLENDE_SYKEPENGESOKNAD,
            narmesteLederId = null,
        )
        planlagtVarselRepository.save(planlagtVarsel)
        log.info("Planlegger varsel ${planlagtVarsel.varselType} for soknad $id som sendes ${planlagtVarsel.sendes}.")
    }

    private fun SykepengesoknadDTO.planleggVarselForStatusSendt() {
        val harAlleredePlanlagt = planlagtVarselRepository.findBySykepengesoknadId(this.id)
            .any { it.varselType == SENDT_SYKEPENGESOKNAD }

        if (harAlleredePlanlagt) {
            log.info("Har allerede planlagt varsel for status SENDT for soknad ${this.id}.")
            return
        }

        val planlagtVarsel = PlanlagtVarsel(
            id = null,
            sykepengesoknadId = this.id,
            brukerFnr = this.fnr,
            oppdatert = Instant.now(),
            orgnummer = this.arbeidsgiver!!.orgnummer!!,
            sendes = narmesteFornuftigDagtid().toInstant(),
            status = PLANLAGT,
            varselType = SENDT_SYKEPENGESOKNAD,
            narmesteLederId = null,
        )
        planlagtVarselRepository.save(planlagtVarsel)
        log.info("Planlegger varsel ${planlagtVarsel.varselType} for soknad $id som sendes ${planlagtVarsel.sendes}.")
    }
}

fun narmesteFornuftigDagtid(now: ZonedDateTime = ZonedDateTime.now(osloZone)): ZonedDateTime {

    val dagtid = if (now.hour < 15) {
        now.withHour(now.hour.coerceAtLeast(9))
    } else {
        now.plusDays(1).withHour(9)
    }
    if (dagtid.dayOfWeek == DayOfWeek.SATURDAY) {
        return dagtid.withHour(9).plusDays(2)
    }
    if (dagtid.dayOfWeek == DayOfWeek.SUNDAY) {
        return dagtid.withHour(9).plusDays(1)
    }
    return dagtid
}

fun omToUkerFornuftigDagtid(now: ZonedDateTime = ZonedDateTime.now(osloZone)): ZonedDateTime {
    return narmesteFornuftigDagtid(now.plusWeeks(2))
}

fun SykepengesoknadDTO.skalSendeVarselTilArbeidsgiver() =
    ArbeidssituasjonDTO.ARBEIDSTAKER == arbeidssituasjon && type != SoknadstypeDTO.REISETILSKUDD

fun SykepengesoknadDTO.bleSendtTilArbeidsgiver() =
    status == SENDT && sendtArbeidsgiver != null && (sendtNav == null || !sendtArbeidsgiver!!.isBefore(sendtNav))

val osloZone: ZoneId = ZoneId.of("Europe/Oslo")
