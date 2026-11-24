package com.example.helloworld;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.retention.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class DatabaseStatusApiTest {

    @Autowired MockMvc mvc;

    @Test
    void reportsDatabaseStatusAsJson() throws Exception {
        mvc.perform(get("/api/status/database"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.connection.up").value(true))
                .andExpect(jsonPath("$.connection.serverVersion").value(Matchers.startsWith("17")))
                .andExpect(jsonPath("$.migrations.currentVersion").value("5"))
                .andExpect(jsonPath("$.migrations.applied").value(5))
                .andExpect(jsonPath("$.migrations.pending").value(0))
                .andExpect(jsonPath("$.tables[*].name", Matchers.hasItems("reviews", "audit_events")))
                .andExpect(jsonPath("$.retention.providers[*].code",
                        Matchers.hasItems("GOOGLE_BUSINESS_PROFILE", "FACEBOOK_PAGES")))
                .andExpect(jsonPath("$.sync.runsLast24h.FAILED").value(0))
                .andExpect(jsonPath("$.checkedAt").isString());
    }

    @Test
    void statusPathServesTheReactApp() throws Exception {
        mvc.perform(get("/status")).andExpect(forwardedUrl("/status/index.html"));
    }
}
