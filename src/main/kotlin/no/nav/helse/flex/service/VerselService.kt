package no.nav.helse.flex.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.OpprettHendelseRequest
import no.nav.helse.flex.client.SyfoservicestranglerClient
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.syfo.kafka.felles.ArbeidssituasjonDTO
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SoknadstypeDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.stereotype.Service

@Service
class VerselService(
    val syfoservicestranglerClient: SyfoservicestranglerClient,
    val identService: IdentService
) {

    val log = logger()

    fun opprettVarsel(soknadString: String) {
        val soknad = soknadString.tilSykepengesoknadDTO()
        log.debug("Mottok soknad ${soknad.id}")

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
        }
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}
