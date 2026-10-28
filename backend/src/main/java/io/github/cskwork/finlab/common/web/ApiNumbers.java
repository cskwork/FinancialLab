package io.github.cskwork.finlab.common.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Round only the public representation; internal FIFO and risk calculations retain precision. */
public final class ApiNumbers {
    private ApiNumbers() { }
    public static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    public static BigDecimal quantity(BigDecimal value) { return value.setScale(8, RoundingMode.HALF_UP); }
    public static BigDecimal derived(BigDecimal value) { return value.setScale(8, RoundingMode.HALF_UP); }
}
