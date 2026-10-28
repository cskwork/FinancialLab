package io.github.cskwork.finlab.transaction.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replays the complete ledger, allocating buy fees to lots and sell fees to proceeds. */
public final class FifoLedger {
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    private FifoLedger() {
    }

    public record Position(String symbol, BigDecimal shares, BigDecimal avgCost) {
    }

    public record Sale(String symbol, BigDecimal qty, BigDecimal proceeds,
                       BigDecimal costBasis, BigDecimal realizedPnL) {
    }

    public record Result(Map<String, Position> holdings, List<Sale> sales) {
        public Result {
            holdings = Collections.unmodifiableMap(new LinkedHashMap<>(holdings));
            sales = List.copyOf(sales);
        }
    }

    private record Lot(BigDecimal qty, BigDecimal unitCost) {
    }

    public static Result replay(List<LedgerEntry> entries) {
        // List.sort is stable: persistence supplies id order to resolve equal timestamps.
        var sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LedgerEntry::timestamp));
        var lots = new LinkedHashMap<String, Deque<Lot>>();
        var sales = new ArrayList<Sale>();

        for (var entry : sorted) {
            switch (entry.type()) {
                case BUY -> {
                    validateTrade(entry);
                    var unitCost = entry.price().add(entry.fee().divide(entry.qty(), PRECISION));
                    lots.computeIfAbsent(entry.symbol(), ignored -> new ArrayDeque<>())
                            .addLast(new Lot(entry.qty(), unitCost));
                }
                case SELL -> {
                    validateTrade(entry);
                    sales.add(sell(entry, lots));
                }
                case DEPOSIT, WITHDRAW -> {
                    // Match the frontend: cash transfers do not affect security positions.
                }
            }
        }

        var positions = new LinkedHashMap<String, Position>();
        lots.forEach((symbol, remaining) -> {
            var shares = BigDecimal.ZERO;
            var cost = BigDecimal.ZERO;
            for (var lot : remaining) {
                shares = shares.add(lot.qty());
                cost = cost.add(lot.qty().multiply(lot.unitCost(), PRECISION));
            }
            positions.put(symbol, new Position(symbol, shares, cost.divide(shares, PRECISION)));
        });
        return new Result(positions, sales);
    }

    private static void validateTrade(LedgerEntry entry) {
        if (entry.symbol() == null || entry.symbol().isEmpty()
                || entry.qty() == null || entry.price() == null) {
            throw new IllegalArgumentException(entry.type() + " 거래는 symbol, qty, price가 필수입니다");
        }
        if (entry.qty().signum() <= 0 || entry.price().signum() <= 0
                || entry.fee() == null || entry.fee().signum() < 0) {
            throw new IllegalArgumentException("거래 수량과 가격은 양수이고 수수료는 0 이상이어야 합니다");
        }
    }

    private static Sale sell(LedgerEntry entry, Map<String, Deque<Lot>> lots) {
        var position = lots.get(entry.symbol());
        if (position == null) {
            throw new IllegalArgumentException(entry.symbol() + "에 대한 보유 주식이 없습니다");
        }
        var remaining = entry.qty();
        var costBasis = BigDecimal.ZERO;
        while (remaining.signum() > 0 && !position.isEmpty()) {
            var lot = position.removeFirst();
            var consumed = remaining.min(lot.qty());
            costBasis = costBasis.add(consumed.multiply(lot.unitCost(), PRECISION));
            remaining = remaining.subtract(consumed);
            if (consumed.compareTo(lot.qty()) < 0) {
                position.addFirst(new Lot(lot.qty().subtract(consumed), lot.unitCost()));
            }
        }
        if (remaining.signum() > 0) {
            throw new IllegalArgumentException(entry.symbol() + " 매도 수량이 보유 수량을 초과합니다");
        }
        if (position.isEmpty()) {
            lots.remove(entry.symbol());
        }
        var proceeds = entry.qty().multiply(entry.price(), PRECISION).subtract(entry.fee());
        return new Sale(entry.symbol(), entry.qty(), proceeds, costBasis, proceeds.subtract(costBasis));
    }
}
