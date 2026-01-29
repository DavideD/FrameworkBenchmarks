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
import java.util.stream.Collectors;

@Singleton
public class PoolCreator implements PgPoolCreator {

    @ConfigProperty(name = "pg.pool.monitoring.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public Pool create(Input input) {
        Vertx vertx = input.vertx();
        List<PgConnectOptions> databases = input.pgConnectOptionsList();
        PoolOptions poolOptions = input.poolOptions();
        PgPool pgPool = (PgPool) PgBuilder
                .pool()
                .connectingTo(databases.stream().map(SqlConnectOptions.class::cast).collect(Collectors.toList()))
                .with(poolOptions)
                .using(vertx)
                .build();
        return enabled ? new MonitoredPool(pgPool) : pgPool;
    }

}