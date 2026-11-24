package com.dcpost.customer.shipment;

import com.dcpost.customer.shipment.service.TrackingCodeGenerator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TrackingCodeGeneratorTest {
    /** Tracking codes use DC followed by 128 random bits encoded as uppercase hex. */
    @Test
    void Test_generate_001() {
        assertThat(new TrackingCodeGenerator().generate()).matches("DC[0-9A-F]{32}");
    }
}
