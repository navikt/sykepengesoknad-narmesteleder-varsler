package no.nav.helse.flex.ident

import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.PdlClient
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class IdentService(
    private val pdlClient: PdlClient,
) {
    @Cacheable("flex-folkeregister-identer-med-historikk")
    fun hentFolkeregisterIdenterMedHistorikkForFnr(fnr: String): FolkeregisterIdenter {
        val folkeregisteridenter =
            pdlClient
                .hentIdenterMedHistorikk(fnr)
                .filter { it.gruppe == FOLKEREGISTERIDENT }

        return FolkeregisterIdenter(
            originalIdent = fnr,
            gjeldende = folkeregisteridenter.singleOrNull { !it.historisk }?.ident,
            andreIdenter = folkeregisteridenter.map { it.ident }.filter { it != fnr }.distinct(),
            originalIdentFunnet = folkeregisteridenter.any { it.ident == fnr },
        )
    }
}

data class FolkeregisterIdenter(
    val originalIdent: String,
    val gjeldende: String?,
    val andreIdenter: List<String>,
    val originalIdentFunnet: Boolean,
) : java.io.Serializable {
    fun alle(): List<String> = listOf(originalIdent) + andreIdenter

    companion object {
        fun kunOriginal(fnr: String) = FolkeregisterIdenter(fnr, null, emptyList(), false)
    }
}
