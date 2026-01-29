package io.quarkus.benchmark.repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.sqlclient.*;
import io.vertx.sqlclient.spi.DatabaseMetadata;

class MonitoredConnection implements SqlConnection {

    private final SqlConnection delegate;
    private final ConnectionBorrowedEvent event;

    MonitoredConnection(SqlConnection delegate, ContextInternal callerContext, ContextInternal connContext, int poolSize) {
        this.delegate = delegate;
        event = new ConnectionBorrowedEvent(connContext.nettyEventLoop() != callerContext.nettyEventLoop(), poolSize);
        event.begin();
    }

    @Override
    public SqlConnection prepare(String sql, Handler<AsyncResult<PreparedStatement>> handler) {
        prepare(sql).onComplete(handler);
        return this;
    }

    @Override
    public Future<PreparedStatement> prepare(String sql) {
        return delegate.prepare(sql);
    }

    @Override
    public SqlConnection prepare(String sql, PrepareOptions options, Handler<AsyncResult<PreparedStatement>> handler) {
        delegate.prepare(sql, options).onComplete(handler);
        return this;
    }

    @Override
    public Future<PreparedStatement> prepare(String sql, PrepareOptions options) {
        return delegate.prepare(sql, options);
    }

    @Override
    public SqlConnection exceptionHandler(Handler<Throwable> handler) {
        return delegate.exceptionHandler(handler);
    }

    @Override
    public SqlConnection closeHandler(Handler<Void> handler) {
        return delegate.closeHandler(handler);
    }

    @Override
    public void begin(Handler<AsyncResult<Transaction>> handler) {
        begin().onComplete(handler);
    }

    @Override
    public Future<Transaction> begin() {
        return delegate.begin();
    }

    @Override
    public Transaction transaction() {
        return delegate.transaction();
    }

    @Override
    public boolean isSSL() {
        return delegate.isSSL();
    }

    @Override
    public Query<RowSet<Row>> query(String sql) {
        return delegate.query(sql);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql) {
        return delegate.preparedQuery(sql);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql, PrepareOptions options) {
        return delegate.preparedQuery(sql, options);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
        close().onComplete(handler);
    }

    @Override
    public Future<Void> close() {
        event.commit();
        return delegate.close();
    }

    @Override
    public DatabaseMetadata databaseMetadata() {
        return delegate.databaseMetadata();
    }
}
