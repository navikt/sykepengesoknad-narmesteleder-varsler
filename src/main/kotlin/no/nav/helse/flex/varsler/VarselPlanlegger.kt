package no.nav.helse.flex.varsler

import no.nav.helse.flex.logger
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import no.nav.syfo.kafka.felles.ArbeidssituasjonDTO.ARBEIDSTAKER
import no.nav.syfo.kafka.felles.SoknadsstatusDTO.*
import no.nav.syfo.kafka.felles.SoknadstypeDTO.GRADERT_REISETILSKUDD
import no.nav.syfo.kafka.felles.SoknadstypeDTO.REISETILSKUDD
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.*

@Component
class VarselPlanlegger(
    private val planlagtVarselRepository: PlanlagtVarselRepository,
    @Value("\${varsel-kafka-timestamp-start}")
    private val varselKafkaTimestampStart: String,
) {

    val log = logger()
    val featureSwitchTimestamp = OffsetDateTime.parse(varselKafkaTimestampStart)

    fun planleggVarsler(soknad: SykepengesoknadDTO, recordTimestamp: Instant) {

        if (featureSwitchTimestamp.toInstant().isAfter(recordTimestamp)) {
            return
        }

        if (!soknad.skalSendeVarselTilArbeidsgiver()) {
            return
        }

        if (soknad.status == NY) {
            soknad.planleggVarselForStatusNy()
        }
        if (soknad.bleSendtTilArbeidsgiver()) {
            soknad.planleggVarselForStatusSendt()
        }
        if (listOf(SENDT, AVBRUTT, SLETTET, KORRIGERT).contains(soknad.status)) {
            soknad.avbrytManglendeSoknadVarsler()
        }
    }

    private fun SykepengesoknadDTO.avbrytManglendeSoknadVarsler() {
        planlagtVarselRepository.findBySykepengesoknadId(id)
            .filter { it.varselType == MANGLENDE_SYKEPENGESOKNAD }
            .forEach {
                if (it.status == PLANLAGT) {
                    log.info("Avbryter planlagt varsler med type $type for id $id")
                    planlagtVarselRepository.save(
                        it.copy(
                            status = PlanlagtVarselStatus.AVBRUTT,
                            oppdatert = Instant.now(),
                        )
                    )
                }
            }
    }

    private fun SykepengesoknadDTO.planleggVarselForStatusNy() {

        val harAlleredePlanlagt = planlagtVarselRepository.findBySykepengesoknadId(id)
            .any { it.varselType == MANGLENDE_SYKEPENGESOKNAD }

        if (harAlleredePlanlagt) {
            log.warn("Har allerede planlagt varsel for status NY for soknad $id")
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
        log.info("Planlegger varsel ${planlagtVarsel.varselType} for soknad $id som sendes ${planlagtVarsel.sendes}")
    }

    private fun SykepengesoknadDTO.planleggVarselForStatusSendt() {
        val harAlleredePlanlagt = planlagtVarselRepository.findBySykepengesoknadId(this.id)
            .any { it.varselType == SENDT_SYKEPENGESOKNAD }

        if (harAlleredePlanlagt) {
            log.warn("Har allerede planlagt varsel for status SENDT for soknad ${this.id}")
            return
        }

        val planlagtVarsel = PlanlagtVarsel(
            id = null,
            sykepengesoknadId = this.id,
            brukerFnr = this.fnr,
            oppdatert = Instant.now(),
            orgnummer = this.arbeidsgiver!!.orgnummer!!,
            sendes = nærmesteFornuftigDagtid().toInstant(),
            status = PLANLAGT,
            varselType = SENDT_SYKEPENGESOKNAD,
            narmesteLederId = null,
        )
        planlagtVarselRepository.save(planlagtVarsel)
        log.info("Planlegger varsel ${planlagtVarsel.varselType} for soknad $id som sendes ${planlagtVarsel.sendes}")
    }
}

fun nærmesteFornuftigDagtid(now: ZonedDateTime = ZonedDateTime.now(osloZone)): ZonedDateTime {

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
    return nærmesteFornuftigDagtid(now.plusWeeks(2))
}

fun SykepengesoknadDTO.skalSendeVarselTilArbeidsgiver() =
    ARBEIDSTAKER == arbeidssituasjon && type != REISETILSKUDD && type != GRADERT_REISETILSKUDD

fun SykepengesoknadDTO.bleSendtTilArbeidsgiver() =
    status == SENDT && sendtArbeidsgiver != null && (sendtNav == null || !sendtArbeidsgiver!!.isBefore(sendtNav))

val osloZone = ZoneId.of("Europe/Oslo")
