package io.quarkus.benchmark.repository;

import io.quarkus.reactive.pg.client.PgPoolCreator;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnectOptions;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Singleton
public class PoolCreator implements PgPoolCreator {

    @ConfigProperty(name = "pg.pool.monitoring.enabled", defaultValue = "false")
    boolean monitoringEnabled;

    @ConfigProperty(name = "pg.pool.per-event-loop.enabled", defaultValue = "false")
    boolean perEventLoopEnabled;

    @Override
    public Pool create(Input input) {
        PgPool pgPool = perEventLoopEnabled ? new PerEventLoopPool(input) : build(input);
        return monitoringEnabled ? new MonitoredPool(pgPool) : pgPool;
    }

    private PgPool build(Input input) {
        Vertx vertx = input.vertx();
        List<PgConnectOptions> databases = input.pgConnectOptionsList();
        PoolOptions poolOptions = input.poolOptions();
        return (PgPool) PgBuilder
                .pool()
                .connectingTo(databases.stream().map(SqlConnectOptions.class::cast).toList())
                .with(poolOptions)
                .using(vertx)
                .build();
    }
}