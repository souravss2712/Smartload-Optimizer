package com.smartload.service.impl;

import com.smartload.exception.TooManyOrdersException;
import com.smartload.model.OptimizeRequest;
import com.smartload.model.OptimizeResponse;
import com.smartload.optimizer.LoadOptimizer;
import com.smartload.service.LoadOptimizerService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoadOptimizerServiceImpl implements LoadOptimizerService {

    private static final int MAX_PARETO_ORDERS = 20;

    private final LoadOptimizer loadOptimizer;
    private final Validator validator;

    @Override
    public OptimizeResponse optimize(OptimizeRequest request) {
        if (request == null) {
            throw new ConstraintViolationException("request body is required", Set.of());
        }

        if (request.getOrders() != null && request.getOrders().size() > OptimizeRequest.MAX_ORDERS) {
            throw new TooManyOrdersException(
                    "orders cannot contain more than " + OptimizeRequest.MAX_ORDERS + " items");
        }

        Set<ConstraintViolation<OptimizeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        int orderCount = request.getOrders().size();
        if (request.getPreferences() != null
                && request.getPreferences().includeParetoOptimalSolutionsOrDefault()
                && orderCount > MAX_PARETO_ORDERS) {
            throw new ConstraintViolationException(
                    "pareto_optimal_solutions supports at most " + MAX_PARETO_ORDERS + " orders",
                    Set.of());
        }

        long startedAt = System.nanoTime();
        OptimizeResponse response = loadOptimizer.optimize(
                request.getTruck(),
                request.getOrders(),
                request.getPreferences());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Optimized {} orders for truck {} in {} ms",
                orderCount, request.getTruck().getId(), elapsedMillis);
        return response;
    }
}
