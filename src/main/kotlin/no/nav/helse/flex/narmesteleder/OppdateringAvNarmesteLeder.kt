package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.objectMapper
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.readValue
import java.time.Instant

@Component
class OppdateringAvNarmesteLeder(
    private val narmesteLederRepository: NarmesteLederRepository,
) {
    val log = logger()

    fun behandleMeldingFraKafka(meldingString: String) {
        val leder =
            try {
                meldingString.tilNarmesteLederLeesah()
            } catch (_: JacksonException) {
                throw IllegalArgumentException("Kunne ikke lese nærmeste leder-melding")
            }
        val lagret = narmesteLederRepository.findByNarmesteLederId(leder.narmesteLederId)
        if (leder.aktivTom != null) {
            if (lagret != null) {
                narmesteLederRepository.delete(lagret)
                log.info("Slettet narmesteleder med id ${leder.narmesteLederId}")
            } else {
                log.info("Ignorerer ny inaktiv narmesteleder med id ${leder.narmesteLederId}")
            }
        } else {
            narmesteLederRepository.save(leder.tilNarmesteLeder(lagret?.id))
            if (lagret != null) {
                log.info("Oppdatert narmesteleder med id ${leder.narmesteLederId}")
            } else {
                log.info("Lagret narmesteleder med id ${leder.narmesteLederId}")
            }
        }
    }

    fun String.tilNarmesteLederLeesah(): NarmesteLederLeesah = objectMapper.readValue(this)
}

private fun NarmesteLederLeesah.tilNarmesteLeder(id: String?): NarmesteLeder =
    NarmesteLeder(
        id = id,
        narmesteLederId = narmesteLederId,
        brukerFnr = fnr,
        orgnummer = orgnummer,
        narmesteLederFnr = narmesteLederFnr,
        narmesteLederTelefonnummer = narmesteLederTelefonnummer,
        narmesteLederEpost = narmesteLederEpost,
        aktivFom = aktivFom,
        arbeidsgiverForskutterer = arbeidsgiverForskutterer,
        timestamp = timestamp.toInstant(),
        oppdatert = Instant.now(),
        aktivTom = null,
    )
