package io.github.cskwork.finlab.integration;

import io.github.cskwork.finlab.transaction.domain.FifoLedger;
import io.github.cskwork.finlab.transaction.domain.LedgerEntry;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import io.github.cskwork.finlab.transaction.persistence.IdempotencyRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Exercises the real security chain, Flyway schema, JPA transactions and PostgreSQL row locks. */
@SpringBootTest(properties = "finlab.ledger.lock-timeout-ms=150")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    private static final EmbeddedPostgres POSTGRES = postgres();
    private static final String PASSWORD = "FinLabDemo!2026";
    private static final String START = "2024-01-02T14:30:00Z";
    private static final String NEXT = "2024-01-03T14:30:00Z";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdempotencyRepository idempotency;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean PasswordEncoder passwordEncoder;
    private String token;
    private long userId;
    private final Map<Object, String> rawResponses = Collections.synchronizedMap(new IdentityHashMap<>());

    private static EmbeddedPostgres postgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        POSTGRES.close();
    }

    @BeforeEach
    void authenticate() throws Exception {
        Map<String, Object> login = object(call(HttpMethod.POST, "/v1/auth/login", null,
                Map.of("email", "demo@finlab.app", "password", PASSWORD), 200));
        token = (String) login.get("token");
        userId = number(login, "userId").longValue();
    }

    @Test
    void migrationsAndLoginUseRealPasswordHashAndSampleMarketData() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
                .isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select password_hash from app_users where email = ?", String.class,
                "demo@finlab.app")).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("select count(distinct symbol) from candles", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from candles", Integer.class)).isEqualTo(310);
        assertThat(token.split("\\.")).hasSize(3);
        assertShape("login", object(call(HttpMethod.POST, "/v1/auth/login", null,
                Map.of("email", "demo@finlab.app", "password", PASSWORD), 200)));
        call(HttpMethod.POST, "/v1/auth/login", null,
                Map.of("email", "demo@finlab.app", "password", "incorrect-password"), 401);
        call(HttpMethod.POST, "/v1/auth/login", null,
                Map.of("email", "missing@finlab.app", "password", "incorrect-password"), 401);
        call(HttpMethod.POST, "/v1/auth/login", null, Map.of("email", "demo@finlab.app"), 400);
    }

    @Test
    void portfolioGoalAndPlanCrudMatchesSharedFixtures() throws Exception {
        Map<String, Object> portfolio = createPortfolio();
        long id = number(portfolio, "id").longValue();
        assertShape("portfolio", portfolio);
        assertShape("portfolio", object(call(HttpMethod.GET, "/v1/portfolios/" + id, token, null, 200)));
        assertShape("portfolios", value(call(HttpMethod.GET, "/v1/portfolios?userId=" + userId, token, null, 200)));
        Map<String, Object> updated = object(call(HttpMethod.PATCH, "/v1/portfolios/" + id, token,
                Map.of("name", "Renamed", "baseCurrency", "KRW"), 200));
        assertThat(updated.get("name")).isEqualTo("Renamed");
        assertThat(updated.get("baseCurrency")).isEqualTo("KRW");
        assertShape("portfolio", updated);

        Map<String, Object> goal = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/goals", token,
                Map.of("portfolioId", id, "name", "Emergency fund", "targetAmount", 10000,
                        "targetDate", "2030-01-01T00:00:00Z", "currentAmount", 250), 201));
        assertShape("goal", goal);
        assertShape("goals", value(call(HttpMethod.GET, "/v1/portfolios/" + id + "/goals", token, null, 200)));

        Map<String, Object> plan = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/plans", token,
                Map.of("portfolioId", id, "symbol", "SPY", "amountPerInterval", 100, "interval", "MONTHLY",
                        "startDate", "2024-01-01T00:00:00Z", "endDate", "2030-01-01T00:00:00Z", "isActive", true), 201));
        assertShape("plan", plan);
        assertShape("plans", value(call(HttpMethod.GET, "/v1/portfolios/" + id + "/plans", token, null, 200)));

        assertShape("delete", value(call(HttpMethod.DELETE, "/v1/goals/" + goal.get("id"), token, null, 200)));
        assertShape("delete", value(call(HttpMethod.DELETE, "/v1/plans/" + plan.get("id"), token, null, 200)));
        assertThat(array(call(HttpMethod.GET, "/v1/portfolios/" + id + "/goals", token, null, 200))).isEmpty();
        assertThat(array(call(HttpMethod.GET, "/v1/portfolios/" + id + "/plans", token, null, 200))).isEmpty();
        assertShape("delete", value(call(HttpMethod.DELETE, "/v1/portfolios/" + id, token, null, 200)));
        call(HttpMethod.GET, "/v1/portfolios/" + id, token, null, 404);
    }

    @Test
    void fullSeedLedgerAgreesWithFrontendHoldingsAndRiskMetrics() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        Map<String, Object> seed = json.readValue(Files.readString(
                Path.of("../frontend/public/data/seeds/sample-transactions.json")), Map.class);
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) seed.get("transactions");
        for (Map<String, Object> sample : transactions) {
            Map<String, Object> body = new java.util.LinkedHashMap<>(sample);
            body.put("portfolioId", id);
            call(HttpMethod.POST, transactionPath(id), token, body, 201);
        }
        assertShape("transactions", value(call(HttpMethod.GET, transactionPath(id), token, null, 200)));
        List<Map<String, Object>> holdings = array(call(HttpMethod.GET, "/v1/portfolios/" + id + "/holdings", token, null, 200));
        assertShape("holdings", holdings);
        assertThat(holdings).hasSize(2);
        Map<String, Object> apple = holdings.stream().filter(row -> row.get("symbol").equals("AAPL")).findFirst().orElseThrow();
        Map<String, Object> spy = holdings.stream().filter(row -> row.get("symbol").equals("SPY")).findFirst().orElseThrow();
        assertThat(number(apple, "shares").doubleValue()).isEqualTo(90);
        // The API rounds average costs to eight decimal places, within half an output ULP.
        assertThat(number(apple, "avgCost").doubleValue()).isCloseTo(195.0394444444, within(0.0000000051));
        assertThat(number(apple, "unrealizedPnL").doubleValue()).isCloseTo(1622.75, within(0.000000001));
        assertThat(number(spy, "shares").doubleValue()).isEqualTo(25);
        assertThat(number(spy, "unrealizedPnL").doubleValue()).isCloseTo(738.25, within(0.000000001));
        Map<String, Object> metrics = object(call(HttpMethod.GET, "/v1/metrics/portfolio/" + id, token, null, 200));
        assertShape("metrics", metrics);
        assertThat(number(metrics, "totalValue").doubleValue()).isCloseTo(31930.3, within(0.000000001));
        assertThat(number(metrics, "cash").doubleValue()).isZero();
        assertThat(number(metrics, "sharpe").doubleValue()).isCloseTo(5.4106641762, within(0.000000001));
    }

    @Test
    void ledgerRejectsOversellBackdatedSellAndDeletingSupportingBuyWithoutPartialWrites() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        Map<String, Object> buy = object(call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201));
        assertShape("transaction", buy);
        call(HttpMethod.POST, transactionPath(id), token, trade(id, "SELL", 11, NEXT), 422);
        call(HttpMethod.POST, transactionPath(id), token, trade(id, "SELL", 1, "2024-01-01T00:00:00Z"), 422);
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(1);
        Map<String, Object> sale = object(call(HttpMethod.POST, transactionPath(id), token, trade(id, "SELL", 7, NEXT), 201));
        call(HttpMethod.DELETE, "/v1/transactions/" + buy.get("id"), token, null, 422);
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(2);
        assertShape("delete", value(call(HttpMethod.DELETE, "/v1/transactions/" + sale.get("id"), token, null, 200)));
        call(HttpMethod.DELETE, "/v1/transactions/" + buy.get("id"), token, null, 200);
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).isEmpty();
    }

    @Test
    void concurrentConflictingSellsSerializeOnThePortfolioRow() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Integer>> attempts = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(trade(id, "SELL", 7, NEXT))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(20, TimeUnit.SECONDS), attempts.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 422);
        }
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(2);
        List<Map<String, Object>> holdings = array(call(HttpMethod.GET, "/v1/portfolios/" + id + "/holdings", token, null, 200));
        assertThat(number(holdings.getFirst(), "shares").doubleValue()).isEqualTo(3);
    }

    @Test
    void simultaneousIdempotentRequestsReturnOneStoredResponseAndOneTransaction() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(trade(id, "BUY", 10, START));
        Callable<MvcResult> create = () -> mvc.perform(post(transactionPath(id))
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        List<MvcResult> responses = simultaneous(create, create);
        assertThat(responses).allSatisfy(response -> assertThat(response.getResponse().getStatus()).isEqualTo(201));
        assertThat(responses.get(0).getResponse().getContentAsString())
                .isEqualTo(responses.get(1).getResponse().getContentAsString());
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where portfolio_id = ? and idempotency_key = ?",
                Integer.class, id, key)).isEqualTo(1);
    }

    @Test
    void deletingBuyAndCreatingDependentSellCannotBothCommit() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        long buyId = number(object(call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201)), "id").longValue();
        List<MvcResult> responses = simultaneous(
                () -> mvc.perform(delete("/v1/transactions/" + buyId).header("Authorization", "Bearer " + token)).andReturn(),
                () -> mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(trade(id, "SELL", 7, NEXT)))).andReturn());
        List<Integer> statuses = responses.stream().map(response -> response.getResponse().getStatus()).toList();
        assertThat(statuses).isIn(List.of(200, 422), List.of(422, 201));
        List<Map<String, Object>> remaining = array(call(HttpMethod.GET, transactionPath(id), token, null, 200));
        assertThat(remaining).hasSize(statuses.getFirst() == 200 ? 0 : 2);
        List<LedgerEntry> ledger = remaining.stream().map(row -> new LedgerEntry(
                TransactionType.valueOf((String) row.get("type")), (String) row.get("symbol"),
                decimal(row.get("qty")), decimal(row.get("price")), decimal(row.get("fee")),
                Instant.parse((String) row.get("timestamp")))).toList();
        var replay = FifoLedger.replay(ledger);
        if (!remaining.isEmpty()) assertThat(replay.holdings().get("AAPL").shares()).isEqualByComparingTo("3");
    }

    @Test
    void portfolioLockTimeoutReturnsConflictAndBodyMismatchIsRejectedBeforeWaiting() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        try (var executor = Executors.newSingleThreadExecutor()) {
            try (var connection = jdbc.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (var statement = connection.prepareStatement("select id from portfolios where id = ? for update")) {
                        statement.setLong(1, id);
                        try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
                    }
                    long started = System.nanoTime();
                    var blocked = executor.submit(() -> call(HttpMethod.POST, transactionPath(id), token,
                            trade(id, "BUY", 1, START), 409));
                    MvcResult result = blocked.get(3, TimeUnit.SECONDS);
                    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
                    assertProblem(object(result), 409);
                    long mismatchStarted = System.nanoTime();
                    call(HttpMethod.POST, transactionPath(id), token, trade(id + 1000, "BUY", 1, START), 400);
                    call(HttpMethod.POST, "/v1/portfolios/" + id + "/goals", token,
                            Map.of("portfolioId", id + 1000, "name", "Mismatch", "targetAmount", 100, "targetDate", NEXT), 400);
                    call(HttpMethod.POST, "/v1/portfolios/" + id + "/plans", token,
                            Map.of("portfolioId", id + 1000, "symbol", "SPY", "amountPerInterval", 100,
                                    "interval", "MONTHLY", "startDate", START), 400);
                    assertThat(Duration.ofNanos(System.nanoTime() - mismatchStarted)).isLessThan(Duration.ofSeconds(1));
                } finally {
                    connection.rollback();
                }
            }
        }
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).isEmpty();
    }

    @Test
    void repositoryPurgesExpiredIdempotencyRecordsAndKeepsLiveKeys() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        String expired = "expired-" + UUID.randomUUID();
        String live = "live-" + UUID.randomUUID();
        for (String key : List.of(expired, live)) {
            MvcResult result = mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(trade(id, "BUY", 1, START)))).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
        }
        Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
        jdbc.update("update idempotency_records set expires_at = ? where portfolio_id = ? and idempotency_key = ?",
                Timestamp.from(cutoff), id, expired);
        Integer removed = new TransactionTemplate(transactions).execute(status -> idempotency.deleteExpiredBefore(cutoff));
        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(idempotency.findByPortfolioIdAndKey(id, expired)).isEmpty();
        assertThat(idempotency.findByPortfolioIdAndKey(id, live)).isPresent();
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(2);
    }

    @Test
    void productionMigrationLocationsDoNotCreatePublicDemoCredentials() throws Exception {
        var baseConfiguration = new YamlPropertySourceLoader()
                .load("production-defaults", new ClassPathResource("application.yml")).getFirst();
        assertThat(baseConfiguration.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        String schema = "production_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres", "postgres"), "postgres", "")
                    .schemas(schema).defaultSchema(schema).createSchemas(true)
                    .locations("classpath:db/migration").load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".app_users", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".candles", Integer.class)).isEqualTo(310);
        } finally {
            // The identifier is a generated lowercase UUID, confined to this disposable test cluster.
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void databaseRejectsCaseVariantsOfAnExistingEmail() {
        assertThatThrownBy(() -> jdbc.update("insert into app_users(email,password_hash,role) "
                        + "select upper(email),password_hash,role from app_users where email = ?", "demo@finlab.app"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from app_users where lower(email) = ?", Integer.class,
                "demo@finlab.app")).isEqualTo(1);
    }

    @Test
    void passwordVerificationRunsAfterTheRealDatabaseTransactionHasEnded() throws Exception {
        AtomicInteger checked = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("BCrypt password verification must not retain a database transaction").isFalse();
            checked.incrementAndGet();
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(any(CharSequence.class), anyString());
        try {
            call(HttpMethod.POST, "/v1/auth/login", null,
                    Map.of("email", "demo@finlab.app", "password", PASSWORD), 200);
            call(HttpMethod.POST, "/v1/auth/login", null,
                    Map.of("email", "demo@finlab.app", "password", "wrong-password"), 401);
            assertThat(checked.get()).isEqualTo(2);
        } finally {
            reset(passwordEncoder);
        }
    }

    @Test
    void transactionDecimalsUseFixedScalePlainJsonAndReadBackWithoutShapeDrift() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        MvcResult created = call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201);
        assertFixedScale(created.getResponse().getContentAsString(), "qty", 8);
        assertFixedScale(created.getResponse().getContentAsString(), "price", 4);
        assertFixedScale(created.getResponse().getContentAsString(), "fee", 4);
        MvcResult listed = call(HttpMethod.GET, transactionPath(id), token, null, 200);
        assertFixedScale(listed.getResponse().getContentAsString(), "qty", 8);
        assertFixedScale(listed.getResponse().getContentAsString(), "price", 4);
        Map<String, Object> tiny = Map.of("portfolioId", id, "type", "BUY", "symbol", "SPY",
                "qty", new BigDecimal("0.00000001"), "price", new BigDecimal("0.0001"), "timestamp", NEXT);
        MvcResult fractional = call(HttpMethod.POST, transactionPath(id), token, tiny, 201);
        assertThat(fractional.getResponse().getContentAsString()).contains("\"qty\":0.00000001", "\"price\":0.0001");
    }

    @Test
    void idempotencyReplaysExactlyAndRejectsChangedBodyWithoutAnotherWrite() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(trade(id, "BUY", 10, START));
        MvcResult first = mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        MvcResult replay = mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        MvcResult mismatch = mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(trade(id, "BUY", 11, START)))).andReturn();
        assertThat(mismatch.getResponse().getStatus()).isEqualTo(422);
        assertShape("error-idempotency", value(mismatch));
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_records where expires_at > now()", Integer.class))
                .isGreaterThanOrEqualTo(1);
        jdbc.update("update idempotency_records set expires_at = now() - interval '1 second' where portfolio_id = ? and idempotency_key = ?", id, key);
        MvcResult afterExpiry = mvc.perform(post(transactionPath(id)).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(afterExpiry.getResponse().getStatus()).isEqualTo(201);
        assertThat(object(afterExpiry).get("id")).isNotEqualTo(object(first).get("id"));
        assertThat(array(call(HttpMethod.GET, transactionPath(id), token, null, 200))).hasSize(2);
    }

    @Test
    void queryFiltersAreInclusiveAndOptionalFieldsAreOmittedRatherThanNull() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201);
        call(HttpMethod.POST, transactionPath(id), token, trade(id, "SELL", 2, NEXT), 201);
        assertThat(array(call(HttpMethod.GET, transactionPath(id) + "?type=BUY&symbol=AAPL&startDate=" + START + "&endDate=" + START,
                token, null, 200))).hasSize(1);
        assertThat(array(call(HttpMethod.GET, transactionPath(id) + "?symbol=MSFT", token, null, 200))).isEmpty();
        Map<String, Object> deposit = object(call(HttpMethod.POST, transactionPath(id), token,
                Map.of("portfolioId", id, "type", "DEPOSIT", "timestamp", NEXT), 201));
        assertThat(deposit).doesNotContainKeys("symbol", "qty", "price", "note");
        assertThat(number(deposit, "fee").doubleValue()).isZero();
        Map<String, Object> plan = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/plans", token,
                Map.of("portfolioId", id, "symbol", "SPY", "amountPerInterval", 100, "interval", "WEEKLY", "startDate", START), 201));
        assertThat(plan).doesNotContainKey("endDate");
        assertThat(plan.get("isActive")).isEqualTo(true);
        Map<String, Object> goal = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/goals", token,
                Map.of("portfolioId", id, "name", "Default amount", "targetAmount", 100, "targetDate", NEXT), 201));
        assertThat(number(goal, "currentAmount").doubleValue()).isZero();
        assertShape("market-data", value(call(HttpMethod.GET,
                "/v1/market-data?symbol=AAPL&startDate=2024-01-02&endDate=2024-01-02", token, null, 200)));
        assertThat(array(call(HttpMethod.GET,
                "/v1/market-data?symbol=AAPL&startDate=2024-01-02&endDate=2024-01-02", token, null, 200))).hasSize(1);
    }

    @Test
    void emptyPortfolioProducesEmptyCollectionsAndFiniteZeroMetrics() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        assertThat(array(call(HttpMethod.GET, "/v1/portfolios/" + id + "/holdings", token, null, 200))).isEmpty();
        Map<String, Object> metrics = object(call(HttpMethod.GET, "/v1/metrics/portfolio/" + id, token, null, 200));
        assertShape("metrics", metrics);
        for (String field : List.of("totalValue", "cash", "pnlDaily", "pnlYTD", "volatility", "sharpe", "maxDrawdown")) {
            assertThat(number(metrics, field).doubleValue()).as(field).isZero();
        }
    }

    @Test
    void securitySeparatesUnauthorizedForbiddenAndHiddenForeignResources() throws Exception {
        long id = number(createPortfolio(), "id").longValue();
        assertShape("error-unauthorized", value(call(HttpMethod.GET, "/v1/portfolios/" + id, null, null, 401)));
        call(HttpMethod.GET, "/v1/portfolios/" + id, "invalid.jwt.signature", null, 401);
        MvcResult forbidden = mvc.perform(get("/v1/portfolios/" + id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))).andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
        assertShape("error-forbidden", value(forbidden));
        String foreignEmail = "foreign-" + UUID.randomUUID() + "@finlab.app";
        jdbc.update("insert into app_users(email,password_hash,role) select ?,password_hash,'USER' from app_users where email = ?",
                foreignEmail, "demo@finlab.app");
        String foreignToken = (String) object(call(HttpMethod.POST, "/v1/auth/login", null,
                Map.of("email", foreignEmail, "password", PASSWORD), 200)).get("token");

        Map<String, Object> buy = object(call(HttpMethod.POST, transactionPath(id), token, trade(id, "BUY", 10, START), 201));
        Map<String, Object> goal = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/goals", token,
                Map.of("portfolioId", id, "name", "Private", "targetAmount", 100, "targetDate", NEXT), 201));
        Map<String, Object> plan = object(call(HttpMethod.POST, "/v1/portfolios/" + id + "/plans", token,
                Map.of("portfolioId", id, "symbol", "SPY", "amountPerInterval", 100, "interval", "WEEKLY", "startDate", START), 201));
        for (String path : List.of("/v1/portfolios/" + id, transactionPath(id), "/v1/portfolios/" + id + "/holdings",
                "/v1/metrics/portfolio/" + id, "/v1/portfolios/" + id + "/goals", "/v1/portfolios/" + id + "/plans",
                "/v1/portfolios?userId=" + userId)) {
            assertShape("error-not-found", value(call(HttpMethod.GET, path, foreignToken, null, 404)));
        }
        call(HttpMethod.PATCH, "/v1/portfolios/" + id, foreignToken, Map.of("name", "Stolen"), 404);
        call(HttpMethod.DELETE, "/v1/portfolios/" + id, foreignToken, null, 404);
        call(HttpMethod.POST, transactionPath(id), foreignToken, trade(id, "BUY", 1, NEXT), 404);
        call(HttpMethod.POST, "/v1/portfolios/" + id + "/goals", foreignToken,
                Map.of("portfolioId", id, "name", "Private", "targetAmount", 100, "targetDate", NEXT), 404);
        call(HttpMethod.POST, "/v1/portfolios/" + id + "/plans", foreignToken,
                Map.of("portfolioId", id, "symbol", "SPY", "amountPerInterval", 100, "interval", "WEEKLY", "startDate", START), 404);
        call(HttpMethod.DELETE, "/v1/transactions/" + buy.get("id"), foreignToken, null, 404);
        call(HttpMethod.DELETE, "/v1/goals/" + goal.get("id"), foreignToken, null, 404);
        call(HttpMethod.DELETE, "/v1/plans/" + plan.get("id"), foreignToken, null, 404);
        call(HttpMethod.POST, "/v1/portfolios", foreignToken,
                Map.of("userId", userId, "name", "Stolen", "baseCurrency", "USD"), 404);
    }

    @Test
    void validationProblemsRequestIdCorsAndOperationalEndpointsAreReviewable() throws Exception {
        MvcResult validation = call(HttpMethod.POST, "/v1/portfolios", token,
                Map.of("userId", userId, "name", "", "baseCurrency", "US"), 400);
        assertThat(validation.getResponse().getContentType()).startsWith("application/problem+json");
        assertShape("error-validation", value(validation));
        Map<String, Object> problem = object(validation);
        assertThat(problem).containsKeys("type", "title", "status", "detail", "code", "message", "details");
        assertThat(number(problem, "status").intValue()).isEqualTo(400);
        Map<?, ?> details = (Map<?, ?>) problem.get("details");
        assertThat((Map<String, ?>) details.get("fieldErrors")).containsKeys("name", "baseCurrency");
        long id = number(createPortfolio(), "id").longValue();
        call(HttpMethod.POST, transactionPath(id), token, trade(id + 1000, "BUY", 1, START), 400);
        call(HttpMethod.POST, transactionPath(id), token,
                Map.of("portfolioId", id, "type", "BUY", "timestamp", START), 400);
        call(HttpMethod.POST, transactionPath(id), token,
                Map.of("portfolioId", id, "type", "BUY", "symbol", "AAPL", "qty", -1, "price", 10, "timestamp", START), 400);
        MvcResult requested = mvc.perform(get("/actuator/health").header("X-Request-Id", "integration-request-123")).andReturn();
        assertThat(requested.getResponse().getStatus()).isEqualTo(200);
        assertThat(requested.getResponse().getHeader("X-Request-Id")).isEqualTo("integration-request-123");
        assertThat(requested.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(mvc.perform(get("/actuator/info")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus()).isIn(401, 403, 404);
        MvcResult cors = mvc.perform(options("/v1/portfolios").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,x-request-id,idempotency-key")).andReturn();
        assertThat(cors.getResponse().getStatus()).isEqualTo(200);
        assertThat(cors.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(mvc.perform(options("/v1/portfolios").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse().getStatus()).isEqualTo(403);
        MvcResult docs = mvc.perform(get("/v3/api-docs")).andReturn();
        assertThat(docs.getResponse().getStatus()).isEqualTo(200);
        assertThat(object(docs)).containsKeys("openapi", "paths", "components");
        var document = json.readTree(docs.getResponse().getContentAsString());
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(document.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(document.at("/paths/~1v1~1auth~1login/post/security").isArray()).isTrue();
        assertThat(document.at("/paths/~1v1~1auth~1login/post/security").size()).isZero();
        MvcResult swagger = mvc.perform(get("/swagger-ui/index.html")).andReturn();
        assertThat(swagger.getResponse().getStatus()).isEqualTo(200);
        assertThat(swagger.getResponse().getContentType()).startsWith("text/html");
        assertThat(swagger.getResponse().getContentAsString()).contains("swagger-ui-bundle.js", "swagger-ui.css");
        assertThat(swagger.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'self'");
        for (String asset : List.of("swagger-ui-bundle.js", "swagger-ui-standalone-preset.js", "swagger-initializer.js", "swagger-ui.css")) {
            MvcResult served = mvc.perform(get("/swagger-ui/" + asset)).andReturn();
            assertThat(served.getResponse().getStatus()).as(asset).isEqualTo(200);
            assertThat(served.getResponse().getContentType()).as(asset)
                    .matches(asset.endsWith(".css") ? "text/css.*" : "(?:text|application)/javascript.*");
        }
        MvcResult swaggerConfiguration = mvc.perform(get("/v3/api-docs/swagger-config")).andReturn();
        assertThat(swaggerConfiguration.getResponse().getStatus()).isEqualTo(200);
        assertThat(requested.getResponse().getHeader("Content-Security-Policy")).doesNotContain("'unsafe-inline'");
    }

    private Map<String, Object> createPortfolio() throws Exception {
        return object(call(HttpMethod.POST, "/v1/portfolios", token,
                Map.of("userId", userId, "name", "Integration " + UUID.randomUUID(), "baseCurrency", "USD"), 201));
    }

    private static String transactionPath(long id) {
        return "/v1/portfolios/" + id + "/transactions";
    }

    private static Map<String, Object> trade(long id, String type, int quantity, String timestamp) {
        return Map.of("portfolioId", id, "type", type, "symbol", "AAPL", "qty", quantity,
                "price", 100, "fee", 1, "timestamp", timestamp, "note", "Integration trade");
    }

    private MvcResult call(HttpMethod method, String path, String bearer, Object body, int status) throws Exception {
        MockHttpServletRequestBuilder request = request(method, path).contentType(MediaType.APPLICATION_JSON);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as("%s %s: %s", method, path,
                result.getResponse().getContentAsString()).isEqualTo(status);
        if (result.getRequest().getRequestURI().startsWith("/v1/")) {
            assertActualFormats(json.readTree(result.getResponse().getContentAsString()), "response");
        }
        return result;
    }

    private Object value(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getRequest().getRequestURI().startsWith("/v1/")) {
            assertActualFormats(json.readTree(body), "response");
        }
        Object parsed = json.readValue(body, Object.class);
        rawResponses.put(parsed, body);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(MvcResult result) throws Exception {
        return (Map<String, Object>) value(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> array(MvcResult result) throws Exception {
        return (List<Map<String, Object>>) value(result);
    }

    private static Number number(Map<String, Object> body, String key) {
        return (Number) body.get(key);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static void assertFixedScale(String body, String field, int scale) {
        assertThat(body).containsPattern("\\\"" + field + "\\\"\\s*:\\s*-?\\d+\\.\\d{" + scale + "}(?=[,}])");
    }

    private static void assertProblem(Map<String, Object> body, int status) {
        assertThat(body).containsKeys("type", "title", "status", "detail", "instance", "code", "message");
        assertThat(number(body, "status").intValue()).isEqualTo(status);
        assertThat(body.get("code")).isInstanceOf(String.class);
        assertThat(body.get("message")).isInstanceOf(String.class);
    }

    private List<MvcResult> simultaneous(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (Callable<MvcResult> request : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private void assertShape(String fixture, Object actual) throws Exception {
        Object expected = json.readValue(Files.readString(Path.of("../contract/" + fixture + ".json")), Object.class);
        assertShape(expected, actual, fixture);
        assertActualFormats(json.valueToTree(actual), fixture);
        if (fixture.startsWith("error-")) {
            var expectedProblem = (Map<?, ?>) expected;
            var actualProblem = (Map<?, ?>) actual;
            assertThat(actualProblem.get("type")).isEqualTo(expectedProblem.get("type"));
            assertThat(actualProblem.get("code")).isEqualTo(expectedProblem.get("code"));
        }
        Path actualDirectory = Path.of("target/contract-actual");
        Files.createDirectories(actualDirectory);
        // Login artifacts include a short-lived JWT signed by the ephemeral test key, never production credentials.
        Files.writeString(actualDirectory.resolve(fixture + ".json"),
                rawResponses.getOrDefault(actual, json.writeValueAsString(actual)));
    }

    private static void assertActualFormats(JsonNode node, String path) {
        if (node.isObject()) {
            Set<String> utcTimestamps = Set.of("createdAt", "updatedAt", "timestamp", "lastUpdated", "asOf",
                    "targetDate", "startDate", "endDate");
            for (var field : node.properties()) {
                String name = field.getKey();
                JsonNode value = field.getValue();
                if (!value.isNull() && utcTimestamps.contains(name)) {
                    assertThat(value.isString()).as(path + "." + name).isTrue();
                    assertThat(value.asString()).as(path + "." + name)
                            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z");
                    assertThat(Instant.parse(value.asString())).isNotNull();
                }
                if (name.equals("email")) {
                    assertThat(value.asString()).as(path + ".email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
                }
                if (name.equals("role")) assertThat(value.asString()).as(path + ".role").isIn("USER", "ADMIN");
                if (name.equals("interval")) assertThat(value.asString()).as(path + ".interval").isIn("WEEKLY", "MONTHLY");
                if (name.equals("type") && node.has("portfolioId") && node.has("fee")) {
                    assertThat(value.asString()).as(path + ".type").isIn("BUY", "SELL", "DEPOSIT", "WITHDRAW");
                }
                if (name.equals("date") && node.has("close")) {
                    assertThat(value.asString()).matches("\\d{4}-\\d{2}-\\d{2}");
                    assertThat(LocalDate.parse(value.asString())).isNotNull();
                }
                assertActualFormats(value, path + "." + name);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) assertActualFormats(element, path + "[]");
        } else if (node.isNumber()) {
            assertThat(node.doubleValue()).as(path).isFinite();
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertShape(Object expected, Object actual, String path) {
        if (expected instanceof Map<?, ?> fields) {
            assertThat(actual).as(path).isInstanceOf(Map.class);
            Map<String, Object> values = (Map<String, Object>) actual;
            // Additional ProblemDetail metadata is compatible; every pinned field must remain present with its type.
            assertThat(values.keySet()).as(path).containsAll((java.util.Collection<String>) fields.keySet());
            fields.forEach((key, value) -> assertShape(value, values.get(key), path + "." + key));
        } else if (expected instanceof List<?> items) {
            assertThat(actual).as(path).isInstanceOf(List.class);
            List<?> values = (List<?>) actual;
            if (!items.isEmpty()) {
                assertThat(values).as(path).isNotEmpty();
                values.forEach(value -> assertShape(items.getFirst(), value, path + "[]"));
            }
        } else if (expected instanceof Number) {
            assertThat(actual).as(path).isInstanceOf(Number.class);
        } else if (expected != null) {
            assertThat(actual).as(path).isInstanceOf(expected.getClass());
        } else {
            assertThat(actual).as(path).isNull();
        }
    }
}
