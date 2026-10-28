package io.github.cskwork.finlab.holding.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cskwork.finlab.holding.application.HoldingService.HoldingView;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HoldingResponseTest {
    @Test
    void roundsOnlyTheHttpRepresentationAndPreservesInternalPrecision() {
        var averageCost = new BigDecimal("195.0394444444444444444444444444444");
        var view = new HoldingView(1, 3, "AAPL", new BigDecimal("90"), averageCost,
                new BigDecimal("19176.3"), new BigDecimal("1622.75"), Instant.EPOCH);

        var response = HoldingController.HoldingResponse.from(view);

        assertThat(response.shares()).isEqualTo(new BigDecimal("90.00000000"));
        assertThat(response.avgCost()).isEqualTo(new BigDecimal("195.03944444"));
        assertThat(response.marketValue()).isEqualTo(new BigDecimal("19176.3000"));
        assertThat(response.unrealizedPnL()).isEqualTo(new BigDecimal("1622.7500"));
        assertThat(response.lastUpdated()).isEqualTo(Instant.EPOCH);
        assertThat(view.avgCost()).isEqualTo(averageCost);
    }
}
