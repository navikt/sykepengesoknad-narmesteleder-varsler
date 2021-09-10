package no.nav.helse.flex.doknotifikasjonvalidator

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import org.apache.logging.log4j.util.Strings

class DoknotifikasjonValidator {
    // Kilde https://github.com/navikt/doknotifikasjon-2/blob/master/knot006/src/main/java/no/nav/doknotifikasjon/consumer/NotifikasjonValidator.java

    fun validate(notifikasjon: NotifikasjonMedkontaktInfo) {
        validateString(notifikasjon, notifikasjon.getBestillingsId(), MAX_STRING_SIZE_MEDIUM, "BestillingsId")
        validateString(notifikasjon, notifikasjon.getBestillerId(), MAX_STRING_SIZE_MEDIUM, "BestillerId")
        validateString(notifikasjon, notifikasjon.getFodselsnummer(), MAX_STRING_SIZE_SMALL, "Fodselsnummer")
        validateString(notifikasjon, notifikasjon.getTittel(), MAX_STRING_SIZE_SMALL, "Tittel")
        validateString(notifikasjon, notifikasjon.getEpostTekst(), MAX_STRING_SIZE_LARGE, "EpostTekst")
        validateString(notifikasjon, notifikasjon.getSmsTekst(), MAX_STRING_SIZE_LARGE, "SmsTekst")
        validateNumberForSnot001(notifikasjon, notifikasjon.getAntallRenotifikasjoner(), "antallRenotifikasjoner")
        validateNumberForSnot001(notifikasjon, notifikasjon.getRenotifikasjonIntervall(), "renotifikasjonIntervall")
        if (Strings.isBlank(notifikasjon.getEpostadresse()) && Strings.isBlank(notifikasjon.getMobiltelefonnummer())) {
            throw RuntimeException(
                String.format(
                    "Feilet med å validere DoknotifikasjonMedKontaktInfo AVRO skjema med bestillingsId=%s. Feilmelding: %s. ",
                    notifikasjon.getBestillingsId(), "FEILET_MUST_HAVE_EITHER_MOBILTELEFONNUMMER_OR_EPOSTADESSE_AS_SETT"
                )
            )
        }
        if (notifikasjon.getAntallRenotifikasjoner() != null && notifikasjon.getAntallRenotifikasjoner() > 0 &&
            !(notifikasjon.getRenotifikasjonIntervall() != null && notifikasjon.getRenotifikasjonIntervall() > 0)
        ) {
            throw RuntimeException(
                String.format(
                    "Feilet med å validere Doknotifikasjon AVRO skjema med bestillingsId=%s. Feilmelding: %s. ",
                    notifikasjon.getBestillingsId(),
                    "FEILET_FIELD_RENOTIFIKASJON_INTERVALL_REQUIRES_ANTALL_RENOTIFIKASJONER"
                )
            )
        }
    }

    fun validateString(notifikasjon: NotifikasjonMedkontaktInfo, string: String?, maxLength: Int, fieldName: String) {
        if (string == null || string.trim { it <= ' ' }.isEmpty() || string.length > maxLength) {
            val addedString = if (string == null || string.trim { it <= ' ' }
                .isEmpty()
            ) " ikke satt" else " har for lang string lengde"
            throw RuntimeException("AVRO skjema Doknotifikasjon er ikke gylding for bestilling med bestillingsId: " + notifikasjon.getBestillingsId() + " " + fieldName + " " + addedString)
        }
    }

    /* Denne funksjonen vil forhindre at feltet antallRenotifikasjoner og renotifikasjonIntervall vil aldri bli støre enn 30*/
    fun validateNumberForSnot001(
        notifikasjon: NotifikasjonMedkontaktInfo,
        numberToValidate: Int?,
        fieldName: String?
    ) {
        if (numberToValidate != null && numberToValidate > 30) {
            throw RuntimeException("AVRO skjema Doknotifikasjon er ikke gylding for bestilling med bestillingsId=" + notifikasjon.getBestillingsId() + "" + fieldName)
        }
    }

    companion object {
        private const val MAX_STRING_SIZE_LARGE = 4000
        private const val MAX_STRING_SIZE_MEDIUM = 100
        private const val MAX_STRING_SIZE_SMALL = 40
    }
}
