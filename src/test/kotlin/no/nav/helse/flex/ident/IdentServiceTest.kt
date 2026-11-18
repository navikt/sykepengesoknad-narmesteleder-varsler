package no.nav.helse.flex.ident

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.NPID
import no.nav.helse.flex.client.pdl.PdlGraphQlFeil
import no.nav.helse.flex.client.pdl.PdlIdent
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.cache.FixedDurationTtlFunction
import org.springframework.data.redis.cache.RedisCache
import java.time.Duration
import java.util.concurrent.TimeUnit

class IdentServiceTest : FellesTestOppsett() {
    @Autowired
    lateinit var identService: IdentService

    @Test
    fun `Filtrerer bort aktørid og npid og finner gjeldende ident`() {
        val gammeltFnr = "12345678911"
        val nyttFnr = "12345678912"
        pdlClientFake.setIdenter(
            gammeltFnr,
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, gammeltFnr, historisk = true),
                PdlIdent(FOLKEREGISTERIDENT, nyttFnr, historisk = false),
                PdlIdent(AKTORID, "aktorid111", historisk = false),
                PdlIdent(NPID, "123456789", historisk = false),
            ),
        )

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(gammeltFnr)

        identer.originalIdent shouldBe gammeltFnr
        identer.gjeldende shouldBe nyttFnr
        identer.andreIdenter shouldBe listOf(nyttFnr)
        identer.originalIdentFunnet shouldBe true
        identer.alle() shouldBe listOf(gammeltFnr, nyttFnr)
    }

    @Test
    fun `Markerer at original ident mangler i svaret fra PDL`() {
        val originalFnr = "12345678921"
        val annetFnr = "12345678922"
        pdlClientFake.setIdenter(
            originalFnr,
            listOf(PdlIdent(FOLKEREGISTERIDENT, annetFnr, historisk = false)),
        )

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(originalFnr)

        identer.originalIdentFunnet shouldBe false
        identer.alle().first() shouldBe originalFnr
        identer.alle() shouldContain annetFnr
    }

    @Test
    fun `Gjeldende er null når alle identer er historiske`() {
        val originalFnr = "12345678931"
        pdlClientFake.setIdenter(
            originalFnr,
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, originalFnr, historisk = true),
                PdlIdent(FOLKEREGISTERIDENT, "12345678932", historisk = true),
            ),
        )

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(originalFnr)

        identer.gjeldende shouldBe null
    }

    @Test
    fun `Bruker cache ved gjentatte oppslag`() {
        val ident = "12345678941"
        pdlClientFake.setIdenter(ident, listOf(PdlIdent(FOLKEREGISTERIDENT, ident, historisk = false)))

        identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)

        await().atMost(5, TimeUnit.SECONDS).until {
            cacheManager.getCache("flex-folkeregister-identer-med-historikk")?.get(ident) != null
        }

        identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)

        pdlClientFake.kall.size shouldBe 1
    }

    @Test
    fun `Feil fra PDL caches ikke`() {
        val ident = "12345678951"
        pdlClientFake.setFeil(ident, PdlGraphQlFeil(listOf("server_error")))

        org.junit.jupiter.api.assertThrows<PdlGraphQlFeil> {
            identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)
        }

        pdlClientFake.setIdenter(ident, listOf(PdlIdent(FOLKEREGISTERIDENT, ident, historisk = false)))
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)

        identer.gjeldende shouldBe ident
        pdlClientFake.kall.size shouldBe 2
    }

    @Test
    fun `TTL i cachen er én time`() {
        val cache = cacheManager.getCache("flex-folkeregister-identer-med-historikk")
        cache.shouldBeInstanceOf<RedisCache>()
        val ttlFunction = cache.cacheConfiguration.ttlFunction
        ttlFunction.shouldBeInstanceOf<FixedDurationTtlFunction>()

        ttlFunction.duration shouldBe Duration.ofHours(1)
    }
}
