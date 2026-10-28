package io.github.cskwork.finlab.transaction.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.config.FinLabProperties;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolation;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import io.github.cskwork.finlab.transaction.domain.IdempotencyRecord;
import io.github.cskwork.finlab.transaction.domain.LedgerEntry;
import io.github.cskwork.finlab.transaction.domain.LedgerTransaction;
import io.github.cskwork.finlab.transaction.domain.TransactionType;
import io.github.cskwork.finlab.transaction.persistence.IdempotencyRepository;
import io.github.cskwork.finlab.transaction.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock PortfolioService portfolios;
    @Mock TransactionRepository transactions;
    @Mock IdempotencyRepository idempotency;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant DAY1 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant DAY2 = Instant.parse("2024-01-02T00:00:00Z");
    private static final Instant DAY3 = Instant.parse("2024-01-03T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private TransactionService service;

    @BeforeEach void setUp() {
        service = new TransactionService(portfolios, transactions, idempotency, json, clock, FinLabProperties.defaults());
    }

    @Test void rejectsZeroTtlAtTheValidatedConfigurationBoundary() {
        assertInvalidDuration(Duration.ZERO);
    }

    @Test void rejectsNegativeTtlAtTheValidatedConfigurationBoundary() {
        assertInvalidDuration(Duration.ofSeconds(-1));
    }

    @Test void commandPreservesNumericScaleAndDefaultsOnlyTheAbsentFee() {
        var command = command(TransactionType.BUY, "10.00000000", "100.0000", null, DAY1);
        assertThat(command.qty()).isEqualTo(new BigDecimal("10.00000000"));
        assertThat(command.price()).isEqualTo(new BigDecimal("100.0000"));
        assertThat(command.fee()).isEqualTo(BigDecimal.ZERO);
        assertThat(command.entry()).isEqualTo(new LedgerEntry(TransactionType.BUY, "AAPL", command.qty(), command.price(), BigDecimal.ZERO, DAY1));
    }

    @Test void depositAllowsAbsentQuantityAndPrice() {
        stubSave();
        var command = new TransactionService.Command(2, TransactionType.DEPOSIT, null, null, null, null, DAY1, "Cash");
        var response = service.create(7, command, null);
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.qty()).isNull();
        assertThat(response.price()).isNull();
        assertThat(response.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.note()).isEqualTo("Cash");
    }

    @Test void createsWithoutIdempotencyAndLocksBeforeLedgerReadAndSave() {
        stubSave();
        var command = buy();
        var response = service.create(7, command, null);
        assertThat(response.id()).isEqualTo(42);
        assertThat(response.portfolioId()).isEqualTo(2);
        assertThat(response.type()).isEqualTo(TransactionType.BUY);
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.qty()).isEqualByComparingTo("10");
        assertThat(response.price()).isEqualByComparingTo("100");
        assertThat(response.fee()).isEqualByComparingTo("1");
        assertThat(response.timestamp()).isEqualTo(DAY1);
        assertThat(response.note()).isEqualTo("Test trade");
        var order = inOrder(portfolios, transactions);
        order.verify(portfolios).lockOwned(2, 7);
        order.verify(transactions).findByPortfolioIdOrderByTimestampAscIdAsc(2);
        order.verify(transactions).saveAndFlush(any());
        verifyNoInteractions(idempotency);
    }

    @Test void firstKeyedRequestStoresResponseAndConfiguredExpiry() throws Exception {
        stubSave();
        var command = buy();
        var response = service.create(7, command, "create-1");
        var captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotency).save(captor.capture());
        IdempotencyRecord record = captor.getValue();
        assertThat(record.getRequestHash()).isEqualTo(fingerprint(command));
        assertThat(record.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(json.readValue(record.getResponseBody(), TransactionService.TransactionView.class)).isEqualTo(response);
    }

    @Test void sameKeyAndEquivalentNumericBodyReplayOriginalResponse() {
        stubSave();
        AtomicReference<IdempotencyRecord> record = new AtomicReference<>();
        when(idempotency.findByPortfolioIdAndKey(2, "same-key")).thenAnswer(i -> Optional.ofNullable(record.get()));
        when(idempotency.save(any())).thenAnswer(i -> { record.set(i.getArgument(0)); return record.get(); });
        var first = service.create(7, buy(), "same-key");
        var replay = service.create(7, command(TransactionType.BUY, "10.0000", "100.0000", "1.0000", DAY1), "same-key");
        assertThat(replay).isEqualTo(first);
        verify(transactions, times(1)).saveAndFlush(any());
        verify(transactions, times(1)).findByPortfolioIdOrderByTimestampAscIdAsc(2);
        verify(idempotency, times(1)).save(any());
        verify(portfolios, times(2)).lockOwned(2, 7);
    }

    @Test void customConfiguredTtlControlsTheStoredExpiry() {
        var properties = new FinLabProperties(null,
                new FinLabProperties.Idempotency(Duration.ofMinutes(10), null), null, null);
        service = new TransactionService(portfolios, transactions, idempotency, json, clock, properties);
        stubSave();

        service.create(7, buy(), "shorter-retention");

        var record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotency).save(record.capture());
        assertThat(record.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test void sameKeyWithAnotherBodyReturns422WithoutWritingLedger() throws Exception {
        var stored = record(buy(), NOW.plusSeconds(60));
        when(idempotency.findByPortfolioIdAndKey(2, "key")).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.create(7, command(TransactionType.BUY, "11", "100", "1", DAY1), "key"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(422);
                    assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
        verifyNoInteractions(transactions);
        verify(idempotency, never()).save(any());
        verify(idempotency, never()).delete(any());
    }

    @Test void expiredKeyIsBulkRemovedBeforeNewResponseIsSaved() throws Exception {
        stubSave();
        var stored = record(buy(), NOW);
        when(idempotency.findByPortfolioIdAndKey(2, "key")).thenReturn(Optional.of(stored));
        var changed = command(TransactionType.BUY, "11", "100", "1", DAY1);
        assertThat(service.create(7, changed, "key").qty()).isEqualByComparingTo("11");
        var order = inOrder(idempotency, transactions);
        order.verify(idempotency).findByPortfolioIdAndKey(2, "key");
        order.verify(idempotency).deleteExpiredKey(2, "key", NOW);
        order.verify(transactions).findByPortfolioIdOrderByTimestampAscIdAsc(2);
        order.verify(transactions).saveAndFlush(any());
        order.verify(idempotency).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "has space", "tab\tkey", "한글"})
    void malformedKeyFailsWithoutReadingOrWritingLedger(String key) {
        assertThatThrownBy(() -> service.create(7, buy(), key))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("VALIDATION_ERROR");
                });
        verify(portfolios).lockOwned(2, 7);
        verifyNoInteractions(transactions, idempotency);
    }

    @Test void keyLongerThan128CharactersIsRejected() {
        assertThatThrownBy(() -> service.create(7, buy(), "x".repeat(129))).isInstanceOf(ApiException.class);
        verifyNoInteractions(transactions, idempotency);
    }

    @Test void keyAtMaximumLengthIsAccepted() {
        stubSave();
        assertThat(service.create(7, buy(), "x".repeat(128)).id()).isEqualTo(42);
        verify(idempotency).save(any());
    }

    @Test void ownershipIsVerifiedBeforeCheckingAnIdempotencyKey() {
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.create(7, buy(), "key"))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.status()).isEqualTo(404));
        verifyNoInteractions(transactions, idempotency);
    }

    @Test void oversellFailsWholeLedgerReplayWithoutSaving() {
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2)).thenReturn(List.of(row(1, buy())));
        var oversell = command(TransactionType.SELL, "11", "110", "1", DAY2);
        assertThatThrownBy(() -> service.create(7, oversell, "key"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(422);
                    assertThat(exception.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(exception.getMessage()).contains("보유 수량을 초과합니다");
                });
        verify(transactions, never()).saveAndFlush(any());
        verify(idempotency, never()).save(any());
    }

    @Test void backdatedSellIsRejectedEvenWhenCurrentHoldingWouldCoverIt() {
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2))
                .thenReturn(List.of(row(1, command(TransactionType.BUY, "10", "100", "1", DAY2))));
        assertThatThrownBy(() -> service.create(7, command(TransactionType.SELL, "1", "110", "1", DAY1), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("보유 주식이 없습니다");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void ledgerVerifiesOwnershipAndProjectsImmutableEntries() {
        var buy = row(1, buy());
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2)).thenReturn(List.of(buy));
        assertThat(service.ledger(2, 7)).containsExactly(buy.toEntry());
        var order = inOrder(portfolios, transactions);
        order.verify(portfolios).requireOwned(2, 7);
        order.verify(transactions).findByPortfolioIdOrderByTimestampAscIdAsc(2);
    }

    @Test void foreignLedgerIsNotRead() {
        doThrow(ApiException.notFound()).when(portfolios).requireOwned(2, 7);
        assertThatThrownBy(() -> service.ledger(2, 7)).isInstanceOf(ApiException.class);
        verifyNoInteractions(transactions);
    }

    @Test void listWithoutFiltersReturnsTheBoundedRepositoryResultInOrder() {
        stubFiltered(history().reversed());
        assertThat(service.list(2, 7, null, null, null, null)).extracting(TransactionService.TransactionView::id)
                .containsExactly(3L, 2L, 1L);
        verify(portfolios).requireOwned(2, 7);
        verify(transactions).findFiltered(eq(2L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(transactions, never()).findByPortfolioIdOrderByTimestampAscIdAsc(anyLong());
    }

    @Test void startDateIsDelegatedToTheDatabaseFilter() {
        stubFiltered(history().subList(1, 3));
        assertThat(service.list(2, 7, DAY2, null, null, null)).extracting(TransactionService.TransactionView::id)
                .containsExactly(2L, 3L);
        verify(transactions).findFiltered(eq(2L), eq(DAY2), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test void endDateIsDelegatedToTheDatabaseFilter() {
        stubFiltered(history().subList(0, 2));
        assertThat(service.list(2, 7, null, DAY2, null, null)).extracting(TransactionService.TransactionView::id)
                .containsExactly(1L, 2L);
        verify(transactions).findFiltered(eq(2L), isNull(), eq(DAY2), isNull(), isNull(), any(Pageable.class));
    }

    @Test void transactionTypeIsDelegatedToTheDatabaseFilter() {
        stubFiltered(List.of(history().get(2)));
        assertThat(service.list(2, 7, null, null, TransactionType.SELL, null)).extracting(TransactionService.TransactionView::id)
                .containsExactly(3L);
        verify(transactions).findFiltered(eq(2L), isNull(), isNull(), eq(TransactionType.SELL), isNull(), any(Pageable.class));
    }

    @Test void symbolIsDelegatedWithoutChangingItsCase() {
        when(transactions.findFiltered(eq(2L), isNull(), isNull(), isNull(), eq("SPY"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(history().get(1))));
        when(transactions.findFiltered(eq(2L), isNull(), isNull(), isNull(), eq("spy"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(2, 7, null, null, null, "SPY")).extracting(TransactionService.TransactionView::id)
                .containsExactly(2L);
        assertThat(service.list(2, 7, null, null, null, "spy")).isEmpty();
    }

    @Test void allFiltersAreDelegatedInOneBoundedQuery() {
        stubFiltered(List.of(history().get(2)));
        assertThat(service.list(2, 7, DAY2, DAY3, TransactionType.SELL, "AAPL"))
                .extracting(TransactionService.TransactionView::id).containsExactly(3L);
        verify(transactions).findFiltered(eq(2L), eq(DAY2), eq(DAY3), eq(TransactionType.SELL), eq("AAPL"), any(Pageable.class));
        verify(transactions, never()).findByPortfolioIdOrderByTimestampAscIdAsc(anyLong());
    }

    @Test void configuredListLimitControlsPageSizeAndStableOrder() {
        var properties = new FinLabProperties(null, null, null, new FinLabProperties.Ledger(3000, 2));
        service = new TransactionService(portfolios, transactions, idempotency, json, clock, properties);
        stubFiltered(history().subList(1, 3).reversed());
        assertThat(service.list(2, 7, null, null, null, null)).extracting(TransactionService.TransactionView::id)
                .containsExactly(3L, 2L);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findFiltered(eq(2L), isNull(), isNull(), isNull(), isNull(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "timestamp", "id"));
    }

    @Test void foreignTransactionListNeverReadsRepository() {
        doThrow(ApiException.notFound()).when(portfolios).requireOwned(2, 7);
        assertThatThrownBy(() -> service.list(2, 7, null, null, null, null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(transactions);
    }

    @Test void missingTransactionDeletionReturns404WithoutLocking() {
        assertThatThrownBy(() -> service.delete(99, 7))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.status()).isEqualTo(404));
        verifyNoInteractions(portfolios);
        verify(transactions, never()).delete(any(LedgerTransaction.class));
    }

    @Test void foreignTransactionCannotBeDeletedOrReadAsALedger() {
        when(transactions.findById(1L)).thenReturn(Optional.of(row(1, buy())));
        doThrow(ApiException.notFound()).when(portfolios).lockOwned(2, 7);
        assertThatThrownBy(() -> service.delete(1, 7)).isInstanceOf(ApiException.class);
        verify(transactions, never()).findByPortfolioIdOrderByTimestampAscIdAsc(anyLong());
        verify(transactions, never()).delete(any(LedgerTransaction.class));
    }

    @Test void deletionOfABuyRequiredByALaterSellIsRejected() {
        var buy = row(1, buy());
        var sell = row(2, command(TransactionType.SELL, "3", "110", "1", DAY2));
        when(transactions.findById(1L)).thenReturn(Optional.of(buy));
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2)).thenReturn(List.of(buy, sell));
        assertThatThrownBy(() -> service.delete(1, 7))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(422);
                    assertThat(exception.getMessage()).contains("이 거래에 의존하는 이후 매도가 있어 삭제할 수 없습니다");
                });
        verify(transactions, never()).delete(any(LedgerTransaction.class));
    }

    @Test void deletingASellKeepsValidRemainingBuys() {
        var buy = row(1, buy());
        var sell = row(2, command(TransactionType.SELL, "3", "110", "1", DAY2));
        when(transactions.findById(2L)).thenReturn(Optional.of(sell));
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2)).thenReturn(List.of(buy, sell));
        service.delete(2, 7);
        var order = inOrder(portfolios, transactions);
        order.verify(transactions).findById(2L);
        order.verify(portfolios).lockOwned(2, 7);
        order.verify(transactions).findByPortfolioIdOrderByTimestampAscIdAsc(2);
        order.verify(transactions).delete(sell);
    }

    @Test void allowsDeletingFromAnAlreadyBrokenLegacyLedgerForRepair() {
        var buy = row(1, buy());
        var invalidSell = row(2, command(TransactionType.SELL, "11", "110", "1", DAY2));
        when(transactions.findById(1L)).thenReturn(Optional.of(buy));
        when(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(2)).thenReturn(List.of(buy, invalidSell));
        service.delete(1, 7);
        verify(transactions).delete(buy);
    }

    private TransactionService.Command buy() { return command(TransactionType.BUY, "10", "100", "1", DAY1); }
    private TransactionService.Command command(TransactionType type, String qty, String price, String fee, Instant timestamp) {
        return new TransactionService.Command(2, type, "AAPL", decimal(qty), decimal(price), decimal(fee), timestamp, "Test trade");
    }
    private BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    private LedgerTransaction row(long id, TransactionService.Command command) {
        var row = new LedgerTransaction(command.portfolioId(), command.entry(), command.note());
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }
    private void stubSave() {
        when(transactions.saveAndFlush(any())).thenAnswer(i -> {
            LedgerTransaction row = i.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 42L);
            return row;
        });
    }
    private List<LedgerTransaction> history() {
        return List.of(row(1, buy()), row(2, new TransactionService.Command(2, TransactionType.BUY, "SPY",
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, DAY2, null)),
                row(3, command(TransactionType.SELL, "1", "110", "1", DAY3)));
    }
    private String fingerprint(TransactionService.Command command) throws Exception {
        var canonical = new FingerprintInput(command.portfolioId(), command.type(), command.symbol(),
                canonical(command.qty()), canonical(command.price()), canonical(command.fee()), command.timestamp(), command.note());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
    }
    private String canonical(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
    private record FingerprintInput(long portfolioId, TransactionType type, String symbol, String qty,
                                    String price, String fee, Instant timestamp, String note) { }
    private void stubFiltered(List<LedgerTransaction> rows) {
        when(transactions.findFiltered(anyLong(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(rows));
    }
    private void assertInvalidDuration(Duration ttl) {
        var properties = new FinLabProperties(null, new FinLabProperties.Idempotency(ttl, null), null, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).extracting(ConstraintViolation::getMessage)
                    .contains("Idempotency durations must be positive");
        }
    }
    private IdempotencyRecord record(TransactionService.Command command, Instant expiresAt) throws Exception {
        var view = TransactionService.TransactionView.from(row(42, command));
        return new IdempotencyRecord(2, "key", fingerprint(command), json.writeValueAsString(view), expiresAt);
    }
}
