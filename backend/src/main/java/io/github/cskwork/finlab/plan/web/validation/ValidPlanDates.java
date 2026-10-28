package io.github.cskwork.finlab.plan.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlanDatesValidator.class)
public @interface ValidPlanDates {
    String message() default "endDate must be on or after startDate";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
