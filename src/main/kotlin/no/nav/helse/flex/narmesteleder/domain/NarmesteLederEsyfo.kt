package no.nav.helse.flex.narmesteleder.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("narmeste_leder_esyfo")
data class NarmesteLederEsyfo(
    @Id val id: String? = null,
    val oppdatert: Instant,
    val timestamp: Instant,
    val narmesteLederId: UUID,
    val orgnummer: String,
    val brukerFnr: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
) {
    fun tilNarmesteLeder() =
        NarmesteLeder(
            id = id,
            oppdatert = oppdatert,
            timestamp = timestamp,
            narmesteLederId = narmesteLederId,
            orgnummer = orgnummer,
            brukerFnr = brukerFnr,
            narmesteLederFnr = narmesteLederFnr,
            narmesteLederTelefonnummer = narmesteLederTelefonnummer,
            narmesteLederEpost = narmesteLederEpost,
            aktivFom = aktivFom,
            aktivTom = aktivTom,
            arbeidsgiverForskutterer = arbeidsgiverForskutterer,
        )
}
