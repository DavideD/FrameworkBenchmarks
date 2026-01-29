package io.quarkus.benchmark.repository;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jdk.jfr.FlightRecorder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FlightRecorderConfig {

    @ConfigProperty(name = "pg.pool.monitoring.enabled", defaultValue = "false")
    boolean enabled;

    void registerEvent(@Observes StartupEvent se) {
        if (enabled) {
            FlightRecorder.register(ConnectionBorrowedEvent.class);
        }
    }
}