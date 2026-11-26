package com.example.helloworld.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PropertiesLoaderUtils
import java.util.Properties

/** The per-profile settings from ADR 0002, section 2, read straight from the properties files. */
class ProfilePropertiesTest {

    private fun load(name: String): Properties = PropertiesLoaderUtils.loadProperties(ClassPathResource(name))

    @Test
    fun `shared file defaults to dev and holds no per-profile values`() {
        val shared = load("application.properties")
        assertThat(shared.getProperty("spring.profiles.default")).isEqualTo("dev")
        assertThat(shared.stringPropertyNames()).doesNotContain(
            "spring.datasource.password", "app.status.enabled", "app.retention.enabled",
        )
    }

    @Test
    fun `dev keeps the local password and status screen and turns retention off`() {
        val dev = load("application-dev.properties")
        assertThat(dev.getProperty("spring.datasource.password")).isEqualTo("noa")
        assertThat(dev.getProperty("app.status.enabled")).isEqualTo("true")
        assertThat(dev.getProperty("app.retention.enabled")).isEqualTo("false")
        assertThat(dev.getProperty("logging.level.com.example.helloworld")).isEqualTo("DEBUG")
    }

    @Test
    fun `staging and prod require the password from the environment and run retention`() {
        for (name in listOf("application-staging.properties", "application-prod.properties")) {
            val props = load(name)
            assertThat(props.getProperty("spring.datasource.password")).`as`(name).isEqualTo("\${DB_PASSWORD}")
            assertThat(props.getProperty("app.status.enabled")).`as`(name).isEqualTo("false")
            assertThat(props.getProperty("app.retention.enabled")).`as`(name).isEqualTo("true")
            assertThat(props.getProperty("logging.level.com.example.helloworld")).`as`(name).isEqualTo("INFO")
        }
    }
}
