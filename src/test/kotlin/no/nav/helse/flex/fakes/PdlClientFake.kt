package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.pdl.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent

class PdlClientFake :
    PdlClient,
    Fake {
    private val identer = mutableMapOf<String, List<PdlIdent>>()
    private val feil = mutableMapOf<String, Exception>()
    private val registrerteKall = mutableListOf<String>()

    val kall: List<String>
        get() = registrerteKall.toList()

    fun setIdenter(
        ident: String,
        identer: List<PdlIdent>,
    ) {
        this.feil.remove(ident)
        this.identer[ident] = identer
    }

    fun setFeil(
        ident: String,
        feil: Exception,
    ) {
        this.identer.remove(ident)
        this.feil[ident] = feil
    }

    override fun reset() {
        identer.clear()
        feil.clear()
        registrerteKall.clear()
    }

    override fun hentIdenterMedHistorikk(ident: String): List<PdlIdent> {
        registrerteKall.add(ident)
        feil[ident]?.let { throw it }
        return identer[ident] ?: listOf(PdlIdent(FOLKEREGISTERIDENT, ident, historisk = false))
    }
}
