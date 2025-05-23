package no.nav.helse.flex.varsler

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder

private const val SMS_TEKST = """
En av dine ansatte har mottatt en digital søknad om sykepenger, men har ikke sendt den inn ennå.
Dersom søknaden ikke allerede er sendt til NAV på papir, bør du be den ansatte sende den digitale søknaden nå.
Logg inn på "Min side - arbeidsgiver" for å se hvem det gjelder.
Vennlig hilsen NAV
"""

const val MANGLENDE_VARSEL_EPOST_TEKST = """
<!DOCTYPE html>
<html>
<body>
<p>Hei.</p>
<p>En av dine ansatte har mottatt en digital søknad om sykepenger, men har ikke sendt den inn ennå.</p>
<p>Dersom søknaden ikke allerede er sendt på papir, bør du be den ansatte sende den digitale søknaden nå.</p>
<p>Logg inn på "Min side - arbeidsgiver" for å se hvem det gjelder.</p>
<p>Du må logge inn med BankID eller tilsvarende for at vi skal være sikre på at søknaden kommer fram til rett person.</p>
<p>Vennlig hilsen</p>
<p> NAV</p>
</body>
</html>
"""

const val MANGLENDE_VARSEL_TITTEL = "Vi mangler en søknad fra din ansatt"

fun skapManglendeSøknadVarsel(
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
        .setTittel(MANGLENDE_VARSEL_TITTEL)
        .setEpostTekst(MANGLENDE_VARSEL_EPOST_TEKST)
        .setSmsTekst(SMS_TEKST)
        .setPrefererteKanaler(listOf(PrefererteKanal.EPOST))
        .build()
