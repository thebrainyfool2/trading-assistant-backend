package com.tradingassistant.platform.executionapp;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.service.ExecutionService;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Small boundary between the application layer and the optional engine.
 *
 * <p>The engine is registered only when the broker is enabled. The platform never
 * constructs or reconfigures it; it asks for the running service and translates
 * its absence into the documented unavailable error. This is what lets the API
 * start and serve system and account endpoints without broker credentials.</p>
 */
@Component
public class ExecutionGateway {

    private final ObjectProvider<ExecutionService> executionService;
    private final ObjectProvider<PortfolioService> portfolioService;

    public ExecutionGateway(ObjectProvider<ExecutionService> executionService,
                            ObjectProvider<PortfolioService> portfolioService) {
        this.executionService = executionService;
        this.portfolioService = portfolioService;
    }

    public boolean available() {
        return executionService.getIfAvailable() != null;
    }

    public ExecutionResult submit(OrderIntent intent) {
        ExecutionService service = executionService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service.submit(intent);
    }

    public ExecutionResult cancel(String engineOrderId) {
        ExecutionService service = executionService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service.cancel(engineOrderId);
    }

    public Optional<PortfolioSnapshot> snapshot() {
        PortfolioService service = portfolioService.getIfAvailable();
        return service == null ? Optional.empty() : Optional.of(service.snapshot());
    }

    public Optional<ExecutionService> service() {
        return Optional.ofNullable(executionService.getIfAvailable());
    }

    private static PlatformException unavailable() {
        return new PlatformException(ErrorCode.ENGINE_UNAVAILABLE,
                "Execution is disabled in this environment.");
    }
}
