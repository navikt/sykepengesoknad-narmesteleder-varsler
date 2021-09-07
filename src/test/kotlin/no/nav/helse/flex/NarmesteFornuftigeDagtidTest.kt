package no.nav.helse.flex

import no.nav.helse.flex.varsler.nærmesteFornuftigDagtid
import no.nav.helse.flex.varsler.osloZone
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class NarmesteFornuftigeDagtidTest {

    @Test
    fun `Tirsdag kl 11 sendes med en gang`() {
        val tirsdagKl11 = ZonedDateTime.of(2021, 9, 1, 11, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(tirsdagKl11)
        narmesteFornuftige `should be equal to` tirsdagKl11
    }

    @Test
    fun `Tirsdag kl 4 sendes kl 9`() {
        val tirsdagKl4 = ZonedDateTime.of(2021, 9, 1, 4, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(tirsdagKl4)
        narmesteFornuftige `should be equal to` tirsdagKl4.withHour(9)
    }

    @Test
    fun `Tirsdag kl 18 sendes kl 9 neste dag`() {
        val tirsdagKl18 = ZonedDateTime.of(2021, 9, 1, 18, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(tirsdagKl18)
        narmesteFornuftige `should be equal to` tirsdagKl18.plusDays(1).withHour(9)
    }

    @Test
    fun `Lørdag kl 12 sendes kl 9 mandag`() {
        val lørdagKl12 = ZonedDateTime.of(2021, 9, 11, 12, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(lørdagKl12)
        narmesteFornuftige `should be equal to` lørdagKl12.plusDays(2).withHour(9)
    }

    @Test
    fun `Søndag kl 12 sendes kl 9 mandag`() {
        val søndagKl12 = ZonedDateTime.of(2021, 9, 12, 12, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(søndagKl12)
        narmesteFornuftige `should be equal to` søndagKl12.plusDays(1).withHour(9)
    }

    @Test
    fun `Fredag kl 23 sendes kl 9 mandag`() {
        val fredagKl23 = ZonedDateTime.of(2021, 9, 10, 23, 12, 0, 0, osloZone)
        val narmesteFornuftige = nærmesteFornuftigDagtid(fredagKl23)
        narmesteFornuftige `should be equal to` fredagKl23.plusDays(3).withHour(9)
    }
}
