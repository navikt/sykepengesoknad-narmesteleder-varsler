package no.nav.helse.flex

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.varsler.narmesteFornuftigDagtid
import no.nav.helse.flex.varsler.osloZone
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class NarmesteFornuftigeDagtidTest {
    @Test
    fun `Tirsdag kl 11 sendes med en gang`() {
        val tirsdagKl11 = ZonedDateTime.of(2021, 9, 1, 11, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(tirsdagKl11)
        narmesteFornuftige shouldBe tirsdagKl11
    }

    @Test
    fun `Tirsdag kl 4 sendes kl 9`() {
        val tirsdagKl4 = ZonedDateTime.of(2021, 9, 1, 4, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(tirsdagKl4)
        narmesteFornuftige shouldBe tirsdagKl4.withHour(9)
    }

    @Test
    fun `Tirsdag kl 18 sendes kl 9 neste dag`() {
        val tirsdagKl18 = ZonedDateTime.of(2021, 9, 1, 18, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(tirsdagKl18)
        narmesteFornuftige shouldBe tirsdagKl18.plusDays(1).withHour(9)
    }

    @Test
    fun `Lørdag kl 12 sendes kl 9 mandag`() {
        val lørdagKl12 = ZonedDateTime.of(2021, 9, 11, 12, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(lørdagKl12)
        narmesteFornuftige shouldBe lørdagKl12.plusDays(2).withHour(9)
    }

    @Test
    fun `Søndag kl 12 sendes kl 9 mandag`() {
        val søndagKl12 = ZonedDateTime.of(2021, 9, 12, 12, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(søndagKl12)
        narmesteFornuftige shouldBe søndagKl12.plusDays(1).withHour(9)
    }

    @Test
    fun `Fredag kl 23 sendes kl 9 mandag`() {
        val fredagKl23 = ZonedDateTime.of(2021, 9, 10, 23, 12, 0, 0, osloZone)
        val narmesteFornuftige = narmesteFornuftigDagtid(fredagKl23)
        narmesteFornuftige shouldBe fredagKl23.plusDays(3).withHour(9)
    }
}
