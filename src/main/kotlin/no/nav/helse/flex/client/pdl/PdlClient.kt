package no.nav.helse.flex.client.pdl

interface PdlClient {
    fun hentIdenterMedHistorikk(ident: String): List<PdlIdent>
}

class PdlGraphQlFeil(
    val koder: List<String>,
) : RuntimeException("PDL svarte med feil. Koder: $koder")

class PdlTekniskFeil(
    message: String,
) : RuntimeException(message)
