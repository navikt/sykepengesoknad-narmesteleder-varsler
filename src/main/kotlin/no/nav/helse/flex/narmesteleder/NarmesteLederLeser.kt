package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NarmesteLederLeser(
    private val gammel: NarmesteLederRepository,
    private val esyfo: NarmesteLederEsyfoRepository,
    @param:Value("\${narmeste-leder.lesekilde:GAMMEL}") private val lesekilde: String,
    @param:Value("\${narmeste-leder.esyfo-consumer-aktiv:false}") private val esyfoConsumerAktiv: Boolean,
) {
    init {
        require(lesekilde in setOf("GAMMEL", "ESYFO")) { "Ukjent lesekilde for nærmeste leder" }
        require(lesekilde != "ESYFO" || esyfoConsumerAktiv) { "ESYFO-lesing krever aktiv esyfo-consumer" }
    }

    fun finnAktive(
        brukerFnr: String,
        orgnummer: String,
    ): List<NarmesteLeder> =
        when (lesekilde) {
            "GAMMEL" -> gammel.findAktiveByBrukerFnrAndOrgnummer(brukerFnr, orgnummer)
            else -> esyfo.findAktiveByBrukerFnrAndOrgnummer(brukerFnr, orgnummer).map { it.tilNarmesteLeder() }
        }
}
