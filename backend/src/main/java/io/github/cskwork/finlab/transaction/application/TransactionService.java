package io.github.cskwork.finlab.transaction.application;

import io.github.cskwork.finlab.common.error.ApiException;
import io.github.cskwork.finlab.config.FinLabProperties;
import io.github.cskwork.finlab.portfolio.application.PortfolioService;
import io.github.cskwork.finlab.transaction.domain.*;
import io.github.cskwork.finlab.transaction.persistence.IdempotencyRepository;
import io.github.cskwork.finlab.transaction.persistence.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TransactionService {
    private final PortfolioService portfolios;
    private final TransactionRepository transactions;
    private final IdempotencyRepository idempotency;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration ttl;
    private final int transactionListLimit;

    public TransactionService(PortfolioService portfolios, TransactionRepository transactions,
                              IdempotencyRepository idempotency, JsonMapper json, Clock clock,
                              FinLabProperties properties) {
        this.portfolios = portfolios;
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.json = json;
        this.clock = clock;
        this.ttl = properties.idempotency().ttl();
        this.transactionListLimit = properties.ledger().transactionListLimit();
    }

    public record Command(long portfolioId, TransactionType type, String symbol, BigDecimal qty,
                          BigDecimal price, BigDecimal fee, Instant timestamp, String note) {
        public Command {
            fee = fee == null ? BigDecimal.ZERO : fee;
        }
        public LedgerEntry entry() { return new LedgerEntry(type, symbol, qty, price, fee, timestamp); }
    }

    public record TransactionView(long id, long portfolioId, TransactionType type, String symbol,
                                  BigDecimal qty, BigDecimal price, BigDecimal fee, Instant timestamp, String note) {
        public static TransactionView from(LedgerTransaction tx) {
            return new TransactionView(tx.getId(), tx.getPortfolioId(), tx.getType(), tx.getSymbol(),
                    tx.getQty(), tx.getPrice(), tx.getFee(), tx.getTimestamp(), tx.getNote());
        }
    }

    public List<LedgerEntry> ledger(long portfolioId, long userId) {
        portfolios.requireOwned(portfolioId, userId);
        return transactions.findByPortfolioIdOrderByTimestampAscIdAsc(portfolioId).stream().map(LedgerTransaction::toEntry).toList();
    }

    public Page<TransactionView> list(long portfolioId, long userId, Instant startDate, Instant endDate,
                                      TransactionType type, String symbol) {
        portfolios.requireOwned(portfolioId, userId);
        var page = PageRequest.of(0, transactionListLimit, Sort.by(Sort.Direction.DESC, "timestamp", "id"));
        return transactions.findFiltered(portfolioId, startDate, endDate, type, symbol, page).map(TransactionView::from);
    }

    @Transactional
    public TransactionView create(long userId, Command command, String key) {
        // This lock serializes both whole-ledger validation and idempotency checks for the portfolio.
        portfolios.lockOwned(command.portfolioId(), userId);
        validateKey(key);
        String hash = fingerprint(command);
        if (key != null) {
            var existing = idempotency.findByPortfolioIdAndKey(command.portfolioId(), key);
            if (existing.isPresent()) {
                var record = existing.orElseThrow();
                switch (IdempotencyPolicy.evaluate(record.getRequestHash(), hash, record.getExpiresAt(), clock.instant())) {
                    case REPLAY -> { return json.readValue(record.getResponseBody(), TransactionView.class); }
                    case MISMATCH -> throw new ApiException(422, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used with a different request");
                    case EXPIRED -> {
                        // A scheduled purge may already have removed it; a bulk delete is safe in either case.
                        idempotency.deleteExpiredKey(command.portfolioId(), key, clock.instant());
                    }
                }
            }
        }
        var ledger = new ArrayList<>(transactions.findByPortfolioIdOrderByTimestampAscIdAsc(command.portfolioId()).stream()
                .map(LedgerTransaction::toEntry).toList());
        ledger.add(command.entry());
        validateLedger(ledger);
        var saved = transactions.saveAndFlush(new LedgerTransaction(command.portfolioId(), command.entry(), command.note()));
        var response = TransactionView.from(saved);
        if (key != null) {
            idempotency.save(new IdempotencyRecord(command.portfolioId(), key, hash,
                    json.writeValueAsString(response), clock.instant().plus(ttl)));
        }
        return response;
    }

    @Transactional
    public void delete(long id, long userId) {
        var target = transactions.findById(id).orElseThrow(ApiException::notFound);
        portfolios.lockOwned(target.getPortfolioId(), userId);
        var ledger = transactions.findByPortfolioIdOrderByTimestampAscIdAsc(target.getPortfolioId());
        var remaining = ledger.stream().filter(tx -> tx.getId() != id).map(LedgerTransaction::toEntry).toList();
        try {
            FifoLedger.replay(remaining);
        } catch (IllegalArgumentException exception) {
            // The mock permits repairs to an already-invalid legacy ledger.
            if (valid(ledger.stream().map(LedgerTransaction::toEntry).toList())) {
                throw ApiException.validation("이 거래에 의존하는 이후 매도가 있어 삭제할 수 없습니다 (" + exception.getMessage() + ")");
            }
        }
        transactions.delete(target);
    }

    private static boolean valid(List<LedgerEntry> entries) {
        try { FifoLedger.replay(entries); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }
    private static void validateLedger(List<LedgerEntry> entries) {
        try { FifoLedger.replay(entries); }
        catch (IllegalArgumentException exception) { throw ApiException.validation(exception.getMessage()); }
    }
    private static void validateKey(String key) {
        if (key != null && !key.matches("[!-~]{1,128}")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Idempotency-Key must contain 1 to 128 visible ASCII characters");
        }
    }
    private String fingerprint(Command command) {
        var canonical = new CanonicalRequest(command.portfolioId(), command.type(), command.symbol(),
                canonicalDecimal(command.qty()), canonicalDecimal(command.price()), canonicalDecimal(command.fee()),
                command.timestamp(), command.note());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static String canonicalDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private record CanonicalRequest(long portfolioId, TransactionType type, String symbol, String qty,
                                    String price, String fee, Instant timestamp, String note) {
    }
}
