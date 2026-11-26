package com.dcpost.customer.shipment;

import com.dcpost.customer.shipment.config.ShipmentSchedulingConfiguration;
import com.dcpost.customer.shipment.service.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentSyncSchedulerTest {
    final Instant now = Instant.parse("2026-11-26T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final ShipmentSyncStateService state = mock(ShipmentSyncStateService.class);
    final ShipmentSyncService sync = mock(ShipmentSyncService.class);

    /** A batch uses the configured limit and isolates an individual worker failure from the next shipment. */
    @Test void Test_runBatch_001() {
        when(state.candidates(now, 2)).thenReturn(List.of(41L, 42L));
        doThrow(new IllegalStateException("private provider detail")).when(sync).sync(41);
        new ShipmentSyncScheduler(state, sync, clock, 2).runBatch();
        var order = inOrder(state, sync);
        order.verify(state).candidates(now, 2);
        order.verify(sync).sync(41);
        order.verify(sync).sync(42);
        verifyNoMoreInteractions(sync);
    }

    /** A failed database scan performs no external work and does not escape the scheduled boundary. */
    @Test void Test_runBatch_002() {
        when(state.candidates(now, 2)).thenThrow(new IllegalStateException("private database detail"));
        assertThatCode(() -> new ShipmentSyncScheduler(state, sync, clock, 2).runBatch()).doesNotThrowAnyException();
        verifyNoInteractions(sync);
    }

    ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(ShipmentSchedulingConfiguration.class)
                .withBean(ShipmentSyncStateService.class, () -> state).withBean(ShipmentSyncService.class, () -> sync)
                .withBean(Clock.class, () -> clock)
                .withPropertyValues("customer.shipment.sync.initial-delay-ms=3600000");
    }

    /** Enabled configuration registers exactly one scheduled task without invoking dependencies at startup. */
    @Test void Test_configuration_001() {
        context().run(context -> {
            assertThat(context).hasSingleBean(ShipmentSyncScheduler.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
            verifyNoInteractions(state, sync);
        });
    }

    /** Disabling synchronization removes automatic scheduling, allowing isolated local tests. */
    @Test void Test_configuration_002() {
        context().withPropertyValues("customer.shipment.sync.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ShipmentSyncScheduler.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }
}
