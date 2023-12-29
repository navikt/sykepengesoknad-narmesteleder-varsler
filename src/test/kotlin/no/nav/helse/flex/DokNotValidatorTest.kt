package no.nav.helse.flex

import no.nav.helse.flex.doknotifikasjonvalidator.DoknotifikasjonValidator
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import no.nav.helse.flex.varsler.skapManglendeSøknadVarsel
import no.nav.helse.flex.varsler.skapSendtSøknadVarsel
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class DokNotValidatorTest {
    // Tester at varslene validerer i henhold til https://github.com/navikt/doknotifikasjon-2/blob/master/knot006/src/main/java/no/nav/doknotifikasjon/consumer/NotifikasjonValidator.java som er kopiert inn hit

    val doknotifikasjonValidator = DoknotifikasjonValidator()
    val narmesteLeder =
        NarmesteLeder(
            id = null,
            narmesteLederId = UUID.randomUUID(),
            brukerFnr = "13068712345",
            orgnummer = "999666333",
            narmesteLederFnr = "13068712345",
            narmesteLederTelefonnummer = "40000000",
            narmesteLederEpost = "test@nav.no",
            aktivFom = LocalDate.now(),
            arbeidsgiverForskutterer = true,
            timestamp = Instant.now(),
            oppdatert = Instant.now(),
        )

    @Test
    fun `validerer sendt søknad varsel`() {
        doknotifikasjonValidator.validate(skapSendtSøknadVarsel(UUID.randomUUID().toString(), narmesteLeder))
    }

    @Test
    fun `validerer manglende søknad varsel`() {
        doknotifikasjonValidator.validate(skapManglendeSøknadVarsel(UUID.randomUUID().toString(), narmesteLeder))
    }
}
