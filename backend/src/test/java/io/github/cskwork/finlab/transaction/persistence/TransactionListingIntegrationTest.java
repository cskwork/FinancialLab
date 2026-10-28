package io.github.cskwork.finlab.transaction.persistence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionListingIntegrationTest {
    private static final EmbeddedPostgres POSTGRES = postgres();
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private long portfolioId;
    private long userId;

    private static EmbeddedPostgres postgres() {
        try { return EmbeddedPostgres.builder().setPort(0).start(); }
        catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    @AfterAll static void stopDatabase() throws IOException { POSTGRES.close(); }

    @BeforeEach void seedRows() {
        userId = jdbc.queryForObject("SELECT id FROM app_users WHERE email = 'demo@finlab.app'", Long.class);
        portfolioId = jdbc.queryForObject("""
                INSERT INTO portfolios(user_id, name, base_currency, created_at, updated_at)
                VALUES (?, 'Listing regression', 'USD', now(), now()) RETURNING id
                """, Long.class, userId);
        // 1003 matching rows exceed the default limit. A newer foreign symbol must not displace one.
        jdbc.update("""
                INSERT INTO ledger_transactions(portfolio_id, type, symbol, qty, price, fee, timestamp, note)
                SELECT ?, 'BUY', 'AAPL', 1, 100, 0, ?::timestamptz + n * interval '1 second', n::text
                FROM generate_series(1,1003) n
                """, portfolioId, START.toString());
        jdbc.update("""
                INSERT INTO ledger_transactions(portfolio_id, type, symbol, qty, price, fee, timestamp)
                VALUES (?, 'SELL', 'SPY', 1, 100, 0, ?::timestamptz + interval '1 day')
                """, portfolioId, START.toString());
    }

    @Test void newestThousandMatchingRowsIncludeTheLatestAndReportUnboundedCount() throws Exception {
        var result = mvc.perform(get("/v1/portfolios/{id}/transactions", portfolioId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(userId)))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1004"))
                .andExpect(jsonPath("$.length()").value(1000))
                .andExpect(jsonPath("$[0].symbol").value("SPY"))
                .andExpect(jsonPath("$[1].note").value("1003"))
                .andExpect(jsonPath("$[999].note").value("5"))
                .andReturn();
        var rows = json.readTree(result.getResponse().getContentAsString());
        for (int i = 1; i < rows.size(); i++) {
            assertThat(Instant.parse(rows.get(i - 1).get("timestamp").asString()))
                    .isAfterOrEqualTo(Instant.parse(rows.get(i).get("timestamp").asString()));
        }
    }

    @Test void allFiltersAreAppliedBeforeLimitAndCountAndCorsExposesCount() throws Exception {
        mvc.perform(get("/v1/portfolios/{id}/transactions", portfolioId)
                        .header("Origin", "http://localhost:5173")
                        .param("symbol", "AAPL").param("type", "BUY")
                        .param("startDate", START.toString()).param("endDate", START.plusSeconds(1002).toString())
                        .with(jwt().jwt(token -> token.subject(Long.toString(userId)))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1002"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("X-Total-Count")))
                .andExpect(jsonPath("$.length()").value(1000))
                .andExpect(jsonPath("$[0].note").value("1002"))
                .andExpect(jsonPath("$[999].note").value("3"));
    }

    @Test void equalTimestampsUseNewestCreationIdFirstAndEmptyFiltersReportZero() throws Exception {
        jdbc.update("""
                INSERT INTO ledger_transactions(portfolio_id, type, symbol, qty, price, fee, timestamp, note)
                VALUES (?, 'BUY', 'AAPL', 1, 100, 0, ?::timestamptz, 'newer-at-same-time')
                """, portfolioId, START.plusSeconds(1003).toString());
        mvc.perform(get("/v1/portfolios/{id}/transactions", portfolioId).param("symbol", "AAPL")
                        .with(jwt().jwt(token -> token.subject(Long.toString(userId)))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1004"))
                .andExpect(jsonPath("$[0].note").value("newer-at-same-time"))
                .andExpect(jsonPath("$[1].note").value("1003"));
        mvc.perform(get("/v1/portfolios/{id}/transactions", portfolioId).param("symbol", "missing")
                        .with(jwt().jwt(token -> token.subject(Long.toString(userId)))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "0"))
                .andExpect(content().json("[]"));
    }
}
