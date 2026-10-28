package io.github.cskwork.finlab.metrics.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cskwork.finlab.metrics.application.MetricsService.Snapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetricSnapshotResponseTest {
    @Test
    void roundsMonetaryFieldsAtTheHttpBoundaryWithoutChangingRatios() {
        var snapshot = new Snapshot(0, 3, Instant.EPOCH, new BigDecimal("31930.30000001"), BigDecimal.ZERO,
                new BigDecimal("1.23445"), new BigDecimal("-1.23445"), 12.123456789,
                5.4106641762, 3.123456789);

        var response = MetricsController.MetricSnapshotResponse.from(snapshot);

        assertThat(response.totalValue()).isEqualTo(new BigDecimal("31930.3000"));
        assertThat(response.cash()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(response.pnlDaily()).isEqualTo(new BigDecimal("1.2345"));
        assertThat(response.pnlYTD()).isEqualTo(new BigDecimal("-1.2345"));
        assertThat(response.sharpe()).isEqualTo(5.4106641762);
        assertThat(response.volatility()).isEqualTo(12.123456789);
        assertThat(response.maxDrawdown()).isEqualTo(3.123456789);
        assertThat(response.asOf()).isEqualTo(Instant.EPOCH);
        assertThat(snapshot.pnlDaily()).isEqualTo(new BigDecimal("1.23445"));
    }
}
