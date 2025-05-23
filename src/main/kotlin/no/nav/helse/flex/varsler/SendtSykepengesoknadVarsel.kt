package no.nav.helse.flex.varsler

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder

private const val SMS_TEKST = """
Hei!
Du har fått tilgang til en søknad om sykepenger for en av dine ansatte.
Logg inn på "Min side - arbeidsgiver" og finn søknaden der. 
Vennlig hilsen NAV
"""

const val SENDT_SYKEPENGESOKNAD_EPOST_TEKST = """
<!DOCTYPE html>
<html>
<body>
<p>Hei.</p>
<p>Du har fått tilgang til en søknad om sykepenger for en av dine ansatte fordi du er meldt inn som nærmeste leder med personalansvar.</p>
<p>Logg inn på "Min side - arbeidsgiver" og finn søknaden der.</p>
<p>Du må logge inn med BankID eller tilsvarende for at vi skal være sikre på at søknaden kommer fram til rett person.</p>
<p>Vennlig hilsen</p>
<p> NAV</p>
</body>
</html>
"""

const val SENDT_SYKEPENGESOKNAD_TITTEL = "Ny søknad om sykepenger"

fun skapSendtSøknadVarsel(
    bestillingsId: String,
    narmesteLeder: NarmesteLeder,
): NotifikasjonMedkontaktInfo =
    NotifikasjonMedkontaktInfo
        .newBuilder()
        .setBestillingsId(bestillingsId)
        .setBestillerId("sykepengesoknad-narmesteleder-varsel")
        .setFodselsnummer(narmesteLeder.narmesteLederFnr)
        .setMobiltelefonnummer(narmesteLeder.narmesteLederTelefonnummer)
        .setEpostadresse(narmesteLeder.narmesteLederEpost)
        .setAntallRenotifikasjoner(0)
        .setRenotifikasjonIntervall(0)
        .setTittel(SENDT_SYKEPENGESOKNAD_TITTEL)
        .setEpostTekst(SENDT_SYKEPENGESOKNAD_EPOST_TEKST)
        .setSmsTekst(SMS_TEKST)
        .setPrefererteKanaler(listOf(PrefererteKanal.EPOST))
        .build()
