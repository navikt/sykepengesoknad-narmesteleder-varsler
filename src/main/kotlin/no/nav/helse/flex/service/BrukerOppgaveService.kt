package no.nav.helse.flex.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.syfoservicestrangler.OpprettHendelseRequest
import no.nav.helse.flex.client.syfoservicestrangler.SyfoservicestranglerClient
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.syfo.kafka.felles.ArbeidssituasjonDTO
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SoknadstypeDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.stereotype.Service

@Service
class BrukerOppgaveService(
    val syfoservicestranglerClient: SyfoservicestranglerClient,
    val identService: IdentService
) {

    val log = logger()

    fun opprettBrukeroppgave(soknadString: String) {
        val soknad = soknadString.tilSykepengesoknadDTO()
        log.info("Mottok sykepengesøknad ${soknad.id}")

        val aktorId = identService.hentAktorIdForFnr(soknad.fnr)

        if ((
            SoknadstypeDTO.ARBEIDSTAKERE == soknad.type ||
                (SoknadstypeDTO.BEHANDLINGSDAGER == soknad.type && soknad.arbeidssituasjon == ArbeidssituasjonDTO.ARBEIDSTAKER)
            ) &&
            SoknadsstatusDTO.SENDT == soknad.status &&
            soknad.sendtArbeidsgiver != null
        ) {
            syfoservicestranglerClient.opprettOppgave(
                OpprettHendelseRequest(
                    soknadId = soknad.id,
                    aktorId = aktorId,
                    orgnummer = soknad.arbeidsgiver?.orgnummer,
                    type = soknad.type.name
                )
            )
            log.info("Opprettet brukeroppgave for sykepengesøknad ${soknad.id}")
        }
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}
