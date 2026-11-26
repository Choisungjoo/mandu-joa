package com.example.helloworld.config

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * Refuses to start staging or production with development-only settings (ADR 0002, section 3).
 *
 * Runs as soon as the configuration is read, before any bean is created, so a bad
 * deployment stops before it connects to the database. Environment variables can
 * override the profile files, so the files alone cannot guarantee these values.
 * Registered in `META-INF/spring.factories`.
 */
class ProfileSafetyCheck : ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        val problems = problems(event.environment)
        check(problems.isEmpty()) {
            "Refusing to start with profile(s) ${event.environment.activeProfiles.toList()}:\n" +
                problems.joinToString("\n") { " - $it" }
        }
    }

    companion object {
        /** The dev-only database password from application-dev.properties. */
        const val DEV_PASSWORD = "noa"

        fun problems(env: Environment): List<String> {
            val prod = env.acceptsProfiles(Profiles.of("prod"))
            val staging = env.acceptsProfiles(Profiles.of("staging"))
            val problems = mutableListOf<String>()

            if (prod) {
                // The status controller treats a missing value as on, so this does too.
                if (env.getProperty("app.status.enabled", Boolean::class.java, true)) {
                    problems += "app.status.enabled must be false in prod (the status screen has no login)"
                }
                if (env.getProperty("app.provider.mode").equals("mock", ignoreCase = true)) {
                    problems += "app.provider.mode must not be mock in prod"
                }
            }
            if (prod || staging) {
                val password = try {
                    env.getProperty("spring.datasource.password")
                } catch (e: IllegalArgumentException) {
                    problems += "the database password is not set: set DB_PASSWORD or SPRING_DATASOURCE_PASSWORD"
                    null
                }
                if (password == DEV_PASSWORD) {
                    problems += "the database password must not be the dev value \"$DEV_PASSWORD\""
                }
            }
            return problems
        }
    }
}
