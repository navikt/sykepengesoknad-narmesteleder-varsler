package no.nav.helse.flex.varsler

import no.nav.helse.flex.ident.FolkeregisterIdenter
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder

// 🔴 Rød sone: avgjør hvem som får vite at en ansatt er sykmeldt.
// Forutsetning: narmesteLedere er ikke tom.
// - Én leder: returner den.
// - Flere: returner den ene der brukerFnr == identer.gjeldende.
// - Flere og gjeldende er null, eller ingen/flere ledere har gjeldende fnr: kast RuntimeException
//   med narmesteLederId-ene i meldingen (aldri fnr).
fun velgNarmesteLeder(
    narmesteLedere: List<NarmesteLeder>,
    identer: FolkeregisterIdenter,
): NarmesteLeder {
    narmesteLedere.singleOrNull()?.let { return it }

    val lederePaGjeldendeFnr =
        identer.gjeldende
            ?.let { gjeldende -> narmesteLedere.filter { it.brukerFnr == gjeldende } }
            .orEmpty()

    return lederePaGjeldendeFnr.singleOrNull()
        ?: throw RuntimeException(
            "Fant ${narmesteLedere.size} nærmeste ledere og kunne ikke velge én ut fra gjeldende ident: " +
                "${narmesteLedere.map { it.narmesteLederId }}.",
        )
}
