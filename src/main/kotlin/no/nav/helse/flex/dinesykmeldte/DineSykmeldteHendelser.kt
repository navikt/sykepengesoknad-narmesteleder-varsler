package no.nav.helse.flex.dinesykmeldte

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.OffsetDateTime

data class DineSykmeldteHendelse(
    val id: String,
    val opprettHendelse: OpprettHendelse? = null,
    val ferdigstillHendelse: FerdigstillHendelse? = null,
)

data class OpprettHendelse(
    val ansattFnr: String,
    val orgnummer: String,
    val oppgavetype: String,
    val lenke: String? = null,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime? = null,
)

data class FerdigstillHendelse(
    val timestamp: OffsetDateTime,
)

fun String.tilDineSykmeldteHendelse(): DineSykmeldteHendelse = objectMapper.readValue(this)

const val OPPGAVETYPE_IKKE_SENDT_SOKNAD = "IKKE_SENDT_SOKNAD"
