package io.github.cskwork.finlab.plan.web;

import io.github.cskwork.finlab.plan.application.PlanService;
import io.github.cskwork.finlab.plan.domain.Plan;
import io.github.cskwork.finlab.plan.domain.PlanInterval;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.cskwork.finlab.common.error.ApiExceptionHandler;
import io.github.cskwork.finlab.common.web.CurrentUserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {
    @Mock PlanService service;
    private MockMvc mvc;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlanController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(currentUser()).build();
    }

    @Test void mismatchedBodyAndPathAre400BeforeCallingTheService() throws Exception {
        mvc.perform(post("/v1/portfolios/2/plans").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":3,"symbol":"AAPL","amountPerInterval":100,"interval":"MONTHLY","startDate":"2026-01-01T00:00:00Z"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test void endBeforeStartIsAFieldValidationErrorWithoutServiceCalls() throws Exception {
        mvc.perform(post("/v1/portfolios/2/plans").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":2,"symbol":"AAPL","amountPerInterval":100,"interval":"MONTHLY",
                 "startDate":"2026-01-01T00:00:00Z","endDate":"2025-12-31T23:59:59Z"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.endDate").value("endDate must be on or after startDate"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z"})
    void acceptsAbsentEqualOrLaterEndDate(String endDate) throws Exception {
        Instant end = endDate == null ? null : Instant.parse(endDate);
        var plan = new Plan(2, "AAPL", new BigDecimal("100"), PlanInterval.MONTHLY, START, end, true);
        ReflectionTestUtils.setField(plan, "id", 5L);
        when(service.create(eq(2L), eq(7L), any())).thenReturn(plan);
        String optionalEnd = endDate == null ? "" : ",\"endDate\":\"" + endDate + "\"";
        mvc.perform(post("/v1/portfolios/2/plans").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":2,"symbol":"AAPL","amountPerInterval":100,"interval":"MONTHLY","startDate":"2026-01-01T00:00:00Z"%s}
                """.formatted(optionalEnd)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
        verify(service).create(2, 7, new PlanService.CreatePlan(2, "AAPL", new BigDecimal("100"), PlanInterval.MONTHLY, START, end, null));
    }

    @Test void absentStartDateRemainsAnIndependentRequiredField() throws Exception {
        mvc.perform(post("/v1/portfolios/2/plans").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":2,"symbol":"AAPL","amountPerInterval":100,"interval":"MONTHLY","endDate":"2026-01-01T00:00:00Z"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.startDate").isString());
        verifyNoInteractions(service);
    }

    @Test void listUsesTheResolvedCurrentUserId() throws Exception {
        when(service.list(2, 7)).thenReturn(List.of());
        mvc.perform(get("/v1/portfolios/2/plans")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).list(2, 7);
    }

    @Test void deleteUsesTheResolvedCurrentUserIdAndReturnsJson() throws Exception {
        mvc.perform(delete("/v1/plans/5")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).delete(5, 7);
    }

    private HandlerMethodArgumentResolver currentUser() {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUserId.class);
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                    NativeWebRequest request, WebDataBinderFactory binderFactory) {
                return 7L;
            }
        };
    }
}
