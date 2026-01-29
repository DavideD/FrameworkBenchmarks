package io.quarkus.benchmark.repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;
import io.vertx.sqlclient.impl.Connection;
import io.vertx.sqlclient.impl.SocketConnectionBase;
import io.vertx.sqlclient.impl.SqlConnectionInternal;

import java.util.function.Function;

/**
 * An implementation of the pool that wraps another pool and creates JFR events related to connection acquisition.
 */
class MonitoredPool implements PgPool {

    private final PgPool delegate;

    MonitoredPool(PgPool delegate) {
        this.delegate = delegate;
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
        getConnection().onComplete(handler);
    }

    @Override
    public Future<SqlConnection> getConnection() {
        ContextInternal callerContext = ContextInternal.current();
        // We're only interested in measuring changes in EL threads
        if (callerContext == null || !callerContext.isEventLoopContext() || !callerContext.inThread()) {
            return delegate.getConnection();
        }
        return delegate.getConnection().map(sqlConnection -> {
            if (sqlConnection instanceof SqlConnectionInternal connInternal) {
                Connection connection = connInternal
                        // Unwrap the pooled connection
                        .unwrap()
                        // Unwrap the base connection
                        .unwrap();
                if (connection instanceof SocketConnectionBase socketConnection) {
                    ContextInternal connContext = (ContextInternal) socketConnection.context();
                    return new MonitoredConnection(sqlConnection, callerContext, connContext, size());
                }
            }
            return sqlConnection;
        });
    }

    @Override
    public Query<RowSet<Row>> query(String s) {
        return delegate.query(s);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s) {
        return delegate.preparedQuery(s);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s, PrepareOptions prepareOptions) {
        return delegate.preparedQuery(s, prepareOptions);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
        delegate.close(handler);
    }

    @Override
    public Future<Void> close() {
        return delegate.close();
    }

    @SuppressWarnings("deprecation")
    @Override
    public PgPool connectHandler(Handler<SqlConnection> handler) {
        return delegate.connectHandler(handler);
    }

    @SuppressWarnings("deprecation")
    @Override
    public PgPool connectionProvider(Function<Context, Future<SqlConnection>> function) {
        return delegate.connectionProvider(function);
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
