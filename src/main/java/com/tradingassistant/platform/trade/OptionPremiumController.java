package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only premium status. The monitor only alerts; it never exits a trade. */
@RestController
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/monitoring/premium",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class OptionPremiumController {

    private final OptionPremiumService premiums;

    public OptionPremiumController(OptionPremiumService premiums) {
        this.premiums = premiums;
    }

    @GetMapping
    public ApiResponse<List<OptionPremiumDtos.PremiumView>> active() {
        return ApiResponse.success(premiums.active().stream()
                .map(OptionPremiumDtos.PremiumView::from).toList());
    }

    @GetMapping("/{tradeId}")
    public ApiResponse<OptionPremiumDtos.PremiumView> get(@PathVariable String tradeId) {
        return ApiResponse.success(OptionPremiumDtos.PremiumView.from(
                premiums.get(tradeId)));
    }
}
