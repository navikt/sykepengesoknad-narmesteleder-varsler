package no.nav.helse.flex.fakes

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class FakesTestConfig {
    @Bean
    @Primary
    fun pdlClientFake(): PdlClientFake = PdlClientFake()
}
