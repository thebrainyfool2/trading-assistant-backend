package com.tradingassistant.common.error;

import com.tradingassistant.broker.error.BrokerExceptionHandler;
import com.tradingassistant.broker.exception.BrokerUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ValidationProbeController())
            .setControllerAdvice(new GlobalExceptionHandler(), new BrokerExceptionHandler())
            .build();

    @Test
    void invalidRequestBodyReturnsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"))
                .andExpect(jsonPath("$.meta.requestId").value("unknown"));
    }

    @Test
    void brokerUnavailableReturnsServiceUnavailableEnvelope() throws Exception {
        mockMvc.perform(get("/test/validation/broker-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BROKER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("broker-secret"))));
    }

    @Test
    void unexpectedExceptionReturnsSafeInternalErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/validation/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @RestController
    @RequestMapping("/test/validation")
    static class ValidationProbeController {

        @PostMapping
        void validate(@Valid @RequestBody ValidationProbe request) {
        }

        @GetMapping("/failure")
        void fail() {
            throw new IllegalStateException("internal detail");
        }

        @GetMapping("/broker-failure")
        void brokerFailure() {
            throw new BrokerUnavailableException("broker-secret");
        }
    }

    record ValidationProbe(@NotBlank String name) {
    }
}
