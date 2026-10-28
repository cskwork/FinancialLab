package io.github.cskwork.finlab.goal.web;

import io.github.cskwork.finlab.goal.application.GoalService;
import io.github.cskwork.finlab.goal.domain.Goal;
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
class GoalControllerTest {
    @Mock GoalService service;
    private MockMvc mvc;
    private static final Instant TARGET = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GoalController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(currentUser()).build();
    }

    @Test void mismatchedBodyAndPathAre400BeforeCallingTheService() throws Exception {
        mvc.perform(post("/v1/portfolios/2/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":3,"name":"Home","targetAmount":1000,"targetDate":"2030-01-01T00:00:00Z"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test void matchingIdsMapTheResolvedUserAndInputToTheService() throws Exception {
        var goal = new Goal(2, "Home", new BigDecimal("1000"), TARGET, BigDecimal.ZERO);
        ReflectionTestUtils.setField(goal, "id", 5L);
        when(service.create(eq(2L), eq(7L), any())).thenReturn(goal);
        mvc.perform(post("/v1/portfolios/2/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"portfolioId":2,"name":"Home","targetAmount":1000,"targetDate":"2030-01-01T00:00:00Z"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5)).andExpect(jsonPath("$.portfolioId").value(2));
        verify(service).create(2, 7, new GoalService.CreateGoal(2, "Home", new BigDecimal("1000"), TARGET, null));
    }

    @Test void listUsesTheResolvedCurrentUserId() throws Exception {
        when(service.list(2, 7)).thenReturn(List.of());
        mvc.perform(get("/v1/portfolios/2/goals")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).list(2, 7);
    }

    @Test void deleteUsesTheResolvedCurrentUserIdAndReturnsJson() throws Exception {
        mvc.perform(delete("/v1/goals/5")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
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
