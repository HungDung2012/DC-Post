package com.dcpost.customer.shipment;

import com.dcpost.customer.common.exception.ApiExceptionHandler;
import com.dcpost.customer.common.web.CorrelationIdFilter;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.shipment.service.ShipmentCreationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShipmentCreationHttpTest {
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureProbe()).setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }

    @RestController static class FailureProbe {
        @GetMapping("/test/shipment/{code}") void shipment(@PathVariable ShipmentCreationException.Code code) {
            throw new ShipmentCreationException(code, code == ShipmentCreationException.Code.FEE_CHANGED
                    ? new BigDecimal("30000") : null);
        }
        @GetMapping("/test/integration/{kind}") void integration(@PathVariable IntegrationException.Kind kind) {
            throw new IntegrationException(kind, false);
        }
    }

    /** Fee conflicts return a fresh amount for explicit customer confirmation and a correlation ID. */
    @Test void Test_handle_001() {
        assertThatCode(() -> mvc.perform(get("/test/shipment/FEE_CHANGED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEE_CHANGED"))
                .andExpect(jsonPath("$.currentFee").value(30000))
                .andExpect(jsonPath("$.correlationId").isNotEmpty()))
                .doesNotThrowAnyException();
    }

    /** Payload conflicts do not expose another shipment or any private request contents. */
    @Test void Test_handle_002() {
        assertThatCode(() -> mvc.perform(get("/test/shipment/IDEMPOTENCY_CONFLICT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.currentFee").doesNotExist()))
                .doesNotThrowAnyException();
    }

    /** Invalid inputs are client errors and exhausted tracking retries are temporarily unavailable. */
    @Test void Test_handle_003() {
        assertThatCode(() -> {
            mvc.perform(get("/test/shipment/INVALID_SHIPMENT")).andExpect(status().isBadRequest());
            mvc.perform(get("/test/shipment/TRACKING_UNAVAILABLE")).andExpect(status().isServiceUnavailable());
        }).doesNotThrowAnyException();
    }

    /** Dependency outages and invalid upstream responses have distinct safe HTTP representations. */
    @Test void Test_handle_004() {
        assertThatCode(() -> {
            mvc.perform(get("/test/integration/UNAVAILABLE")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("EXTERNAL_UNAVAILABLE"));
            mvc.perform(get("/test/integration/INVALID_RESPONSE")).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("EXTERNAL_INVALID_RESPONSE"));
        }).doesNotThrowAnyException();
    }
}
