package no.nav.helse.flex.varsler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.helse.flex.ident.FolkeregisterIdenter
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NarmesteLederValgTest {
    @Test
    fun `Velger eneste leder`() {
        val leder = lagLeder("11111111111")

        velgNarmesteLeder(listOf(leder), identer(gjeldende = null)) shouldBe leder
        velgNarmesteLeder(listOf(leder), identer(gjeldende = "22222222222")) shouldBe leder
    }

    @Test
    fun `Velger lederen registrert på gjeldende fnr når det finnes ledere på flere identer`() {
        val historiskLeder = lagLeder("11111111111")
        val gjeldendeLeder = lagLeder("22222222222")

        velgNarmesteLeder(
            listOf(historiskLeder, gjeldendeLeder),
            identer(gjeldende = "22222222222"),
        ) shouldBe gjeldendeLeder
    }

    @Test
    fun `Kaster når flere ledere og gjeldende ident mangler`() {
        shouldThrow<RuntimeException> {
            velgNarmesteLeder(
                listOf(lagLeder("11111111111"), lagLeder("22222222222")),
                identer(gjeldende = null),
            )
        }
    }

    @Test
    fun `Kaster når ingen av lederne er registrert på gjeldende fnr`() {
        shouldThrow<RuntimeException> {
            velgNarmesteLeder(
                listOf(lagLeder("11111111111"), lagLeder("22222222222")),
                identer(gjeldende = "33333333333"),
            )
        }
    }

    @Test
    fun `Kaster når flere ledere er registrert på gjeldende fnr`() {
        shouldThrow<RuntimeException> {
            velgNarmesteLeder(
                listOf(lagLeder("11111111111"), lagLeder("11111111111")),
                identer(gjeldende = "11111111111"),
            )
        }
    }

    @Test
    fun `Feilmeldingen inneholder ikke fnr`() {
        val fnr = listOf("11111111111", "22222222222")
        val feil =
            shouldThrow<RuntimeException> {
                velgNarmesteLeder(
                    fnr.map(::lagLeder),
                    identer(gjeldende = null),
                )
            }

        fnr.forEach { feil.message.orEmpty() shouldNotContain it }
    }

    private fun identer(gjeldende: String?) =
        FolkeregisterIdenter(
            originalIdent = "00000000000",
            gjeldende = gjeldende,
            andreIdenter = listOfNotNull(gjeldende).filter { it != "00000000000" },
            originalIdentFunnet = true,
        )

    private fun lagLeder(
        brukerFnr: String,
        narmesteLederId: UUID = UUID.randomUUID(),
    ) = NarmesteLeder(
        oppdatert = Instant.now(),
        timestamp = Instant.now(),
        narmesteLederId = narmesteLederId,
        orgnummer = "999111555",
        brukerFnr = brukerFnr,
        narmesteLederFnr = "99999999999",
        narmesteLederTelefonnummer = "12345678",
        narmesteLederEpost = "leder@example.no",
        aktivFom = LocalDate.now(),
        arbeidsgiverForskutterer = null,
    )
}
