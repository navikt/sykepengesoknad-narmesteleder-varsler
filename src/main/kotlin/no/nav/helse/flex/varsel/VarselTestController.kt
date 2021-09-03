package no.nav.helse.flex.varsel

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.domain.NarmesteLeder
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.logger
import no.nav.security.token.support.core.api.Unprotected
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.util.*

@RestController
@Unprotected
@RequestMapping(value = ["/test"])
class VarselTestController(val kafkaProducer: Producer<String, NotifikasjonMedkontaktInfo>) {

    val log = logger()

    @ResponseBody
    @GetMapping(value = ["/varsel"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendTestVarsel(): String {

        try {
            val narmesteLeder = NarmesteLeder(
                id = null,
                narmesteLederId = UUID.randomUUID(),
                brukerFnr = "63109447724",
                orgnummer = "orgnummer",
                narmesteLederFnr = "63109447724",
                narmesteLederTelefonnummer = "48184949",
                narmesteLederEpost = "havard.stigen.andersen@gmail.com",
                aktivFom = LocalDate.now(),
                arbeidsgiverForskutterer = true,
                timestamp = Instant.now(),
                oppdatert = Instant.now()
            )
            val bestillingsId = UUID.randomUUID().toString()

            fun skapNySøknadNotifikasjonMedBeggeKanaler(
                bestillingsId: String,
                narmesteLeder: NarmesteLeder
            ): NotifikasjonMedkontaktInfo {
                return NotifikasjonMedkontaktInfo.newBuilder()
                    .setBestillingsId(bestillingsId)
                    .setBestillerId("sykepengesoknad-narmesteleder-varsel")
                    .setFodselsnummer(narmesteLeder.narmesteLederFnr)
                    .setMobiltelefonnummer(narmesteLeder.narmesteLederTelefonnummer)
                    .setEpostadresse(narmesteLeder.narmesteLederEpost)
                    .setAntallRenotifikasjoner(0)
                    .setRenotifikasjonIntervall(0)
                    .setTittel("Ny søknad om sykepenger")
                    .setEpostTekst(EPOST_TEKST)
                    .setSmsTekst(SMS_TEKST)
                    .setPrefererteKanaler(listOf(PrefererteKanal.EPOST, PrefererteKanal.SMS)).build()
            }

            val notifikasjon = skapNySøknadNotifikasjonMedBeggeKanaler(bestillingsId, narmesteLeder)

            val recordMetadata = kafkaProducer.send(
                ProducerRecord(
                    doknotifikasjonTopic,
                    null,
                    bestillingsId,
                    notifikasjon
                )
            ).get()
            return "Sent " + recordMetadata.offset() + " " + recordMetadata.partition()
        } catch (e: Exception) {
            log.error("oops", e)
            return "Auda " + e.message
        }
    }
}
