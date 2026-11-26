package com.example.helloworld.config

import com.example.helloworld.HelloWorldApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.mock.env.MockEnvironment

class ProfileSafetyCheckTest {

    private fun env(profile: String, vararg props: Pair<String, String>) = MockEnvironment().apply {
        setActiveProfiles(profile)
        props.forEach { (k, v) -> setProperty(k, v) }
    }

    private fun problems(profile: String, vararg props: Pair<String, String>) =
        ProfileSafetyCheck.problems(env(profile, *props))

    private val safe = arrayOf(
        "app.status.enabled" to "false",
        "spring.datasource.url" to "jdbc:postgresql://db:5432/mandujoa",
        "spring.datasource.username" to "mandujoa",
        "spring.datasource.password" to "s3cret",
    )

    @Test
    fun `dev allows the dev password and the status screen`() {
        assertThat(problems("dev", "spring.datasource.password" to "noa", "app.status.enabled" to "true")).isEmpty()
    }

    @Test
    fun `prod with safe settings starts`() {
        assertThat(problems("prod", *safe)).isEmpty()
        assertThat(problems("prod", *safe, "app.provider.mode" to "live")).isEmpty()
    }

    @Test
    fun `prod refuses the status screen, also when the setting is missing`() {
        assertThat(problems("prod", *safe, "app.status.enabled" to "true")).singleElement().asString().contains("app.status.enabled")
        val withoutStatus = safe.filterNot { it.first == "app.status.enabled" }.toTypedArray()
        assertThat(problems("prod", *withoutStatus)).singleElement().asString().contains("app.status.enabled")
    }

    @Test
    fun `prod refuses mock providers`() {
        assertThat(problems("prod", *safe, "app.provider.mode" to "mock")).singleElement().asString().contains("mock")
    }

    @Test
    fun `staging may switch the status screen on`() {
        assertThat(problems("staging", *safe, "app.status.enabled" to "true")).isEmpty()
    }

    @Test
    fun `staging and prod refuse the dev password`() {
        for (profile in listOf("staging", "prod")) {
            assertThat(problems(profile, *safe, "spring.datasource.password" to "noa"))
                .`as`(profile).singleElement().asString().contains("\"noa\"")
        }
    }

    @Test
    fun `staging and prod report a missing password clearly`() {
        for (profile in listOf("staging", "prod")) {
            assertThat(problems(profile, *safe, "spring.datasource.password" to "\${DB_PASSWORD}"))
                .`as`(profile).singleElement().asString().contains("DB_PASSWORD")
        }
    }

    @Test
    fun `staging and prod report a missing database URL and user`() {
        for (profile in listOf("staging", "prod")) {
            assertThat(problems(profile, "app.status.enabled" to "false", "spring.datasource.password" to "s3cret"))
                .`as`(profile)
                .anySatisfy { assertThat(it).contains("SPRING_DATASOURCE_URL") }
                .anySatisfy { assertThat(it).contains("SPRING_DATASOURCE_USERNAME") }
                .hasSize(2)
        }
    }

    @Test
    fun `the real application stops before connecting to the database`() {
        // No database is running for this test, so getting this message proves the check ran first.
        assertThatThrownBy {
            SpringApplicationBuilder(HelloWorldApplication::class.java)
                .profiles("prod")
                // Command-line arguments override the profile files, as environment variables would.
                .run(
                    "--app.status.enabled=true",
                    "--spring.datasource.url=jdbc:postgresql://db:5432/mandujoa",
                    "--spring.datasource.username=mandujoa",
                    "--spring.datasource.password=s3cret",
                )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Refusing to start").hasMessageContaining("app.status.enabled")
    }
}
