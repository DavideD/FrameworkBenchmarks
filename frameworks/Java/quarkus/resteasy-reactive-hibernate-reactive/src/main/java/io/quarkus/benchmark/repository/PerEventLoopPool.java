package io.quarkus.benchmark.repository;

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.quarkus.logging.Log;
import io.quarkus.reactive.pg.client.PgPoolCreator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * An implementation of the pool that delegates most invocations to an event-loop bound pool.
 */
class PerEventLoopPool implements PgPool {

    private final VertxInternal vertx;
    private final List<PgPool> pools;

    // We need a default pool to use when pool methods are invoked on a-non Vert.x thread
    // To avoid issues, it is fixed at creation time
    // It should mostly (if not only) used by Hibernate for setup when the application starts
    private final PgPool defaultPool;

    private final FastThreadLocal<PgPool> delegate = new FastThreadLocal<>();

    PerEventLoopPool(PgPoolCreator.Input input) {
        vertx = (VertxInternal) input.vertx();
        PoolOptions poolOptions = input.poolOptions();
        List<PgConnectOptions> databases = input.pgConnectOptionsList();

        List<ContextInternal> creationContexts = new ArrayList<>();
        for (EventExecutor eventExecutor : vertx.getEventLoopGroup()) {
            creationContexts.add(vertx.createEventLoopContext((EventLoop) eventExecutor, null, Thread.currentThread().getContextClassLoader()));
        }

        int eventLoopCount = creationContexts.size();
        pools = new CopyOnWriteArrayList<>();

        int perEventLoopMaxSize = poolOptions.getMaxSize() / eventLoopCount;

        Log.info("Creating " + eventLoopCount + " event-loop specific pools");
        CountDownLatch latch = new CountDownLatch(eventLoopCount);
        for (int i = 0; i < eventLoopCount; i++) {
            int poolIdx = i;

            ContextInternal ctx = creationContexts.get(i);
            // If connections cannot be spread evenly, the extra connections go to the first pool
            int maxSize = perEventLoopMaxSize + (i == 0 ? poolOptions.getMaxSize() % eventLoopCount : 0);

            ctx.runOnContext(v -> {
                PgPool pgPool = (PgPool) PgBuilder
                        .pool()
                        .connectingTo(databases.stream().map(PgConnectOptions::new).map(SqlConnectOptions.class::cast).toList())
                        .with(new PoolOptions(poolOptions)
                                .setEventLoopSize(0) // force use of the current event-loop for all connections in this pool
                                .setMaxSize(maxSize))
                        .using(vertx)
                        .build();
                pools.add(pgPool);
                Log.info("Created pool #" + poolIdx + " with maximum size of " + maxSize);
                delegate.set(pgPool);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        for (ContextInternal creationContext : creationContexts) {
            creationContext.close();
        }

        defaultPool = pools.getFirst();
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
        getConnection().onComplete(handler);
    }

    @Override
    public Future<SqlConnection> getConnection() {
        return getPgPool().getConnection();
    }

    private PgPool getPgPool() {
        PgPool pgPool = delegate.getIfExists();
        if (pgPool != null) {
            return pgPool;
        }
        return defaultPool;
    }

    @Override
    public Query<RowSet<Row>> query(String s) {
        return getPgPool().query(s);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s) {
        return getPgPool().preparedQuery(s);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s, PrepareOptions prepareOptions) {
        return getPgPool().preparedQuery(s, prepareOptions);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
        close().onComplete(handler);
    }

    @Override
    public Future<Void> close() {
        List<Future<Void>> futures = new ArrayList<>(pools.size());
        for (PgPool pgPool : pools) {
            futures.add(pgPool.close());
        }
        pools.clear();
        for (EventExecutor executor : vertx.getEventLoopGroup()) {
            executor.execute(() -> delegate.remove());
        }
        return Future.join(futures).mapEmpty();
    }

    @SuppressWarnings("deprecation")
    @Override
    public PgPool connectHandler(Handler<SqlConnection> handler) {
        for (PgPool pool : pools) {
            pool.connectHandler(handler);
        }
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public PgPool connectionProvider(Function<Context, Future<SqlConnection>> function) {
        for (PgPool pool : pools) {
            pool.connectionProvider(function);
        }
        return this;
    }

    @Override
    public int size() {
        // Does not return the sum on purpose
        // If this instance is wrapped in a MonitoredPool, we want the value of the pool associated to the event loop
        return getPgPool().size();
    }
}