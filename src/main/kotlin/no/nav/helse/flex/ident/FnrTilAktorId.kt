package no.nav.helse.flex.ident

import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.PdlClient
import org.springframework.stereotype.Component

@Component
class FnrTilAktorId(private val pdlClient: PdlClient) {

    fun hentAktorIdForFnr(fnr: String): String {
        val hentPerson = pdlClient.hentIdenter(fnr)
        val ident = hentPerson.hentIdenter?.identer?.find { it.gruppe == AKTORID }?.ident
        return ident
            ?: throw RuntimeException("Kunne ikke finne aktør id for fnr")
    }
}
