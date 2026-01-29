package io.quarkus.benchmark.repository;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.moditect.jfrunit.JfrEventTest;
import org.moditect.jfrunit.JfrEvents;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.moditect.jfrunit.ExpectedEvent.event;

@JfrEventTest
@QuarkusTest
@TestProfile(MonitoredPoolDisabledTest.DisabledProfile.class)
public class MonitoredPoolDisabledTest {

    public JfrEvents jfrEvents = new JfrEvents();

    @Inject
    Vertx vertx;

    @Inject
    Pool pool;

    @Test
    public void testEventIsNotEmitted() {
        pool.withConnection(conn -> conn.query("SELECT 1").execute())
                // Run on a Vert.x context
                // Otherwise, events are not emitted
                .runSubscriptionOn(command -> vertx.getOrCreateContext().runOnContext(command))
                .await()
                .indefinitely();

        jfrEvents.awaitEvents();

        assertThat(jfrEvents.filter(event(ConnectionBorrowedEvent.class.getName()))).isEmpty();
    }

    public static class DisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("pg.pool.monitoring.enabled", "false");
        }
    }
}