package no.nav.helse.flex.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.OpprettHendelseRequest
import no.nav.helse.flex.client.SyfoservicestranglerClient
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.stereotype.Service

@Service
class VerselService(
    val syfoservicestranglerClient: SyfoservicestranglerClient
) {

    val log = logger()

    fun opprettVarsel(soknadString: String) {
        val soknad = soknadString.tilSykepengesoknadDTO()
        log.info("Mottok soknad ${soknad.id}")

        syfoservicestranglerClient.opprettOppgave(
            OpprettHendelseRequest(
                soknadId = soknad.id,
                aktorId = null, // TODO: trengs det?
                orgnummer = null, // TODO: trengs det?
                type = soknad.type.name // TODO: samme som søknadstype eller meldingstype?
            )
        )
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}
