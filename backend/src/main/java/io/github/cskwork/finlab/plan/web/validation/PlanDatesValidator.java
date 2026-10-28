package io.github.cskwork.finlab.plan.web.validation;

import io.github.cskwork.finlab.plan.web.PlanController.CreatePlanRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PlanDatesValidator implements ConstraintValidator<ValidPlanDates, CreatePlanRequest> {
    @Override
    public boolean isValid(CreatePlanRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startDate() == null || request.endDate() == null
                || !request.endDate().isBefore(request.startDate())) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate").addConstraintViolation();
        return false;
    }
}
