package no.nav.helse.flex.varsler

import no.nav.helse.flex.ident.FolkeregisterIdenter
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder

// 🔴 Rød sone: avgjør hvem som får vite at en ansatt er sykmeldt. Skrives av utvikleren.
// Forutsetning: narmesteLedere er ikke tom.
// - Én leder: returner den.
// - Flere: returner den ene der brukerFnr == identer.gjeldende.
// - Flere og gjeldende er null, eller ingen/flere ledere har gjeldende fnr: kast RuntimeException
//   med narmesteLederId-ene i meldingen (aldri fnr).
fun velgNarmesteLeder(
    narmesteLedere: List<NarmesteLeder>,
    identer: FolkeregisterIdenter,
): NarmesteLeder = TODO("🔴 Rød sone: implementeres av utvikleren")
