package no.nav.helse.flex.varsler.domain

import org.springframework.data.annotation.Id
import java.time.Instant
import java.util.*

data class PlanlagtVarsel(
    @Id
    val id: String? = null,
    val oppdatert: Instant,
    val orgnummer: String,
    val brukerFnr: String,
    val sykepengesoknadId: String,
    val status: PlanlagtVarselStatus,
    val varselType: PlanlagtVarselType,
    val sendes: Instant,
    val narmesteLederId: UUID?,
    val dineSykmeldteHendelseOpprettet: Instant? = null,
    val dineSykmeldteHendelseFerdigstilt: Instant? = null
)

enum class PlanlagtVarselStatus {
    PLANLAGT, SENDT, AVBRUTT, INGEN_LEDER
}

enum class PlanlagtVarselType {
    SENDT_SYKEPENGESOKNAD, MANGLENDE_SYKEPENGESOKNAD
}
