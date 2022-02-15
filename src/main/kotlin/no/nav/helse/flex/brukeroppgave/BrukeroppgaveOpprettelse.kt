package no.nav.helse.flex.brukeroppgave

import no.nav.helse.flex.client.syfoservicestrangler.OpprettHendelseRequest
import no.nav.helse.flex.client.syfoservicestrangler.SyfoservicestranglerClient
import no.nav.helse.flex.ident.FnrTilAktorId
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.springframework.stereotype.Component

@Component
class BrukeroppgaveOpprettelse(
    val syfoservicestranglerClient: SyfoservicestranglerClient,
    val fnrTilAktorId: FnrTilAktorId
) {

    val log = logger()

    fun opprettBrukeroppgave(soknad: SykepengesoknadDTO) {

        if ((
            SoknadstypeDTO.ARBEIDSTAKERE == soknad.type ||
                (SoknadstypeDTO.BEHANDLINGSDAGER == soknad.type && soknad.arbeidssituasjon == ArbeidssituasjonDTO.ARBEIDSTAKER) ||
                (SoknadstypeDTO.GRADERT_REISETILSKUDD == soknad.type && soknad.arbeidssituasjon == ArbeidssituasjonDTO.ARBEIDSTAKER)
            ) &&
            SoknadsstatusDTO.SENDT == soknad.status &&
            soknad.sendtArbeidsgiver != null
        ) {
            val aktorId = fnrTilAktorId.hentAktorIdForFnr(soknad.fnr)
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
}
