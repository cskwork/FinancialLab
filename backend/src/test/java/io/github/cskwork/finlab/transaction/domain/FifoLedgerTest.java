package io.github.cskwork.finlab.transaction.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.cskwork.finlab.transaction.domain.TransactionType.BUY;
import static io.github.cskwork.finlab.transaction.domain.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FifoLedgerTest {
    @Test
    void emptyLedgerHasNoPositionsOrSales() {
        var result = FifoLedger.replay(List.of());
        assertThat(result.holdings()).isEmpty();
        assertThat(result.sales()).isEmpty();
    }

    @Test
    void buyFeeIsPartOfUnitCost() {
        var position = FifoLedger.replay(List.of(trade(BUY, "100", "150", "1", 1)))
                .holdings().get("TEST");
        assertThat(position.shares()).isEqualByComparingTo("100");
        assertThat(position.avgCost()).isEqualByComparingTo("150.01");
    }

    @Test
    void averagesTheRemainingLotsIncludingTheirFees() {
        var result = FifoLedger.replay(List.of(
                trade(BUY, "100", "150", "1", 1),
                trade(BUY, "50", "160", "0.5", 2)));
        assertThat(result.holdings().get("TEST").shares()).isEqualByComparingTo("150");
        assertThat(result.holdings().get("TEST").avgCost().doubleValue())
                .isCloseTo(153.3433333333, within(0.0000000001));
    }

    @Test
    void partialSaleAcrossLotsUsesFifoAndBothKindsOfFee() {
        var result = FifoLedger.replay(List.of(
                trade(BUY, "10", "100", "10", 1),
                trade(BUY, "5", "120", "5", 2),
                trade(SELL, "12", "150", "6", 3)));
        // Sold cost: 10 * 101 + 2 * 121 = 1252; proceeds: 12 * 150 - 6 = 1794.
        var sale = result.sales().getFirst();
        assertThat(sale.symbol()).isEqualTo("TEST");
        assertThat(sale.qty()).isEqualByComparingTo("12");
        assertThat(sale.costBasis()).isEqualByComparingTo("1252");
        assertThat(sale.proceeds()).isEqualByComparingTo("1794");
        assertThat(sale.realizedPnL()).isEqualByComparingTo("542");
        assertThat(result.holdings().get("TEST").shares()).isEqualByComparingTo("3");
        assertThat(result.holdings().get("TEST").avgCost()).isEqualByComparingTo("121");
    }

    @Test
    void partialSaleWithinLotRetainsProportionalBuyFee() {
        var result = FifoLedger.replay(List.of(
                trade(BUY, "100", "150", "1", 1),
                trade(SELL, "30", "160", "0.5", 2)));
        assertThat(result.holdings().get("TEST").shares()).isEqualByComparingTo("70");
        assertThat(result.holdings().get("TEST").avgCost()).isEqualByComparingTo("150.01");
        assertThat(result.sales().getFirst().realizedPnL()).isEqualByComparingTo("299.2");
    }

    @Test
    void fractionalSaleCanTurnGainIntoLossAfterFees() {
        var result = FifoLedger.replay(List.of(
                trade(BUY, "0.5", "100", "1", 1),
                trade(SELL, "0.5", "104", "2", 2)));
        var sale = result.sales().getFirst();
        assertThat(sale.costBasis()).isEqualByComparingTo("51");
        assertThat(sale.proceeds()).isEqualByComparingTo("50");
        assertThat(sale.realizedPnL()).isEqualByComparingTo("-1");
        assertThat(result.holdings()).isEmpty();
    }

    @Test
    void fullSaleRemovesPositionAndAllowsNewLot() {
        var result = FifoLedger.replay(List.of(
                trade(BUY, "1", "100", "0", 1),
                trade(SELL, "1", "110", "0", 2),
                trade(BUY, "1", "120", "0", 3)));
        assertThat(result.sales().getFirst().realizedPnL()).isEqualByComparingTo("10");
        assertThat(result.holdings().get("TEST").avgCost()).isEqualByComparingTo("120");
    }

    @Test
    void sortingDoesNotMutateCallerAndResolvesBackdatedTrades() {
        var entries = new ArrayList<>(List.of(
                trade(SELL, "1", "110", "0", 2),
                trade(BUY, "2", "100", "0", 1)));
        var original = List.copyOf(entries);
        assertThat(FifoLedger.replay(entries).holdings().get("TEST").shares())
                .isEqualByComparingTo("1");
        assertThat(entries).isEqualTo(original);
    }

    @Test
    void equalTimestampsPreserveInputOrder() {
        var buy = trade(BUY, "1", "100", "0", 1);
        var sell = trade(SELL, "1", "110", "0", 1);
        assertThat(FifoLedger.replay(List.of(buy, sell)).holdings()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> FifoLedger.replay(List.of(sell, buy)))
                .withMessage("TEST에 대한 보유 주식이 없습니다");
    }

    @Test
    void missingPositionIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FifoLedger.replay(List.of(trade(SELL, "1", "100", "0", 1))))
                .withMessage("TEST에 대한 보유 주식이 없습니다");
    }

    @Test
    void oversellIsRejectedWithoutAffectingSubsequentReplay() {
        var buy = trade(BUY, "1", "100", "0", 1);
        var sell = trade(SELL, "2", "110", "0", 2);
        assertThatIllegalArgumentException().isThrownBy(() -> FifoLedger.replay(List.of(buy, sell)))
                .withMessage("TEST 매도 수량이 보유 수량을 초과합니다");
        assertThat(FifoLedger.replay(List.of(buy)).holdings().get("TEST").shares())
                .isEqualByComparingTo("1");
    }

    @Test
    void deletingBuyRequiredByLaterSellMakesTheLedgerInvalid() {
        var first = trade(BUY, "1", "100", "0", 1);
        var second = trade(BUY, "1", "120", "0", 2);
        var sell = trade(SELL, "2", "130", "0", 3);
        assertThat(FifoLedger.replay(List.of(first, second, sell)).holdings()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> FifoLedger.replay(List.of(second, sell)))
                .withMessageContaining("보유 수량을 초과합니다");
    }

    @Test
    void differentSymbolsHaveIndependentLots() {
        var other = new LedgerEntry(BUY, "OTHER", decimal("3"), decimal("400"),
                decimal("3"), Instant.parse("2024-01-01T00:00:00Z"));
        var result = FifoLedger.replay(List.of(trade(BUY, "1", "100", "1", 1), other));
        assertThat(result.holdings()).containsOnlyKeys("TEST", "OTHER");
        assertThat(result.holdings().get("OTHER").avgCost()).isEqualByComparingTo("401");
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = {"DEPOSIT", "WITHDRAW"})
    void cashTransfersDoNotCreateSecurityPositions(TransactionType type) {
        var transfer = new LedgerEntry(type, null, null, null, BigDecimal.ZERO,
                Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(FifoLedger.replay(List.of(transfer)).holdings()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = {"BUY", "SELL"})
    void tradesRequireAllFields(TransactionType type) {
        var timestamp = Instant.parse("2024-01-01T00:00:00Z");
        for (var invalid : List.of(
                new LedgerEntry(type, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, timestamp),
                new LedgerEntry(type, "", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, timestamp),
                new LedgerEntry(type, "TEST", null, BigDecimal.ONE, BigDecimal.ZERO, timestamp),
                new LedgerEntry(type, "TEST", BigDecimal.ONE, null, BigDecimal.ZERO, timestamp))) {
            assertThatIllegalArgumentException().isThrownBy(() -> FifoLedger.replay(List.of(invalid)))
                    .withMessage(type + " 거래는 symbol, qty, price가 필수입니다");
        }
    }

    @Test
    void nonpositiveQuantitiesPricesAndNegativeFeesAreRejected() {
        for (var invalid : List.of(
                trade(BUY, "0", "100", "0", 1),
                trade(BUY, "-1", "100", "0", 1),
                trade(BUY, "1", "0", "0", 1),
                trade(BUY, "1", "-100", "0", 1),
                trade(BUY, "1", "100", "-1", 1),
                new LedgerEntry(BUY, "TEST", BigDecimal.ONE, BigDecimal.ONE, null, Instant.EPOCH))) {
            assertThatIllegalArgumentException().isThrownBy(() -> FifoLedger.replay(List.of(invalid)))
                    .withMessageContaining("수수료는 0 이상");
        }
    }

    @Test
    void resultCollectionsAreImmutable() {
        var result = FifoLedger.replay(List.of(trade(BUY, "1", "100", "0", 1)));
        assertThatThrownBy(() -> result.holdings().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.sales().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bundledSeedMatchesTheFrontendHandComputedResults() throws Exception {
        var seed = JsonMapper.builder().build().readTree(Files.readString(
                Path.of("..", "frontend", "public", "data", "seeds", "sample-transactions.json")));
        var entries = new ArrayList<LedgerEntry>();
        for (var row : seed.get("transactions")) {
            entries.add(new LedgerEntry(
                    TransactionType.valueOf(row.get("type").asString()),
                    row.has("symbol") ? row.get("symbol").asString() : null,
                    row.has("qty") ? new BigDecimal(row.get("qty").asString()) : null,
                    row.has("price") ? new BigDecimal(row.get("price").asString()) : null,
                    new BigDecimal(row.get("fee").asString()),
                    Instant.parse(row.get("timestamp").asString())));
        }
        var result = FifoLedger.replay(entries);
        var apple = result.holdings().get("AAPL");
        var spy = result.holdings().get("SPY");
        assertThat(apple.shares()).isEqualByComparingTo("90");
        assertThat(spy.shares()).isEqualByComparingTo("25");
        assertThat(apple.avgCost().doubleValue()).isCloseTo(195.0394444444, within(0.0000000001));
        assertThat(spy.avgCost()).isEqualByComparingTo("480.63");
        // 1622.75 / 738.25 are unrealized, unlike the original brief's label.
        assertThat(apple.shares().multiply(decimal("213.07").subtract(apple.avgCost())).doubleValue())
                .isCloseTo(1622.75, within(0.00000001));
        assertThat(spy.shares().multiply(decimal("510.16").subtract(spy.avgCost())).doubleValue())
                .isCloseTo(738.25, within(0.00000001));
        assertThat(result.sales().get(0).realizedPnL()).isEqualByComparingTo("231.15");
        assertThat(result.sales().get(1).realizedPnL()).isEqualByComparingTo("313.55");
    }

    private static LedgerEntry trade(TransactionType type, String qty, String price, String fee, int day) {
        return new LedgerEntry(type, "TEST", decimal(qty), decimal(price), decimal(fee),
                Instant.parse("2024-01-%02dT00:00:00Z".formatted(day)));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
