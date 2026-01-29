package io.quarkus.benchmark.repository;

import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Label("Connection borrowed")
@Description("This event begins when a connection is borrowed and ends when it's returned to the pool")
public class ConnectionBorrowedEvent extends Event {

    @Label("Stolen from another event loop")
    @Description("Whether the event loop running the connection is different from the event loop borrowing the connection")
    public final boolean stolen;

    @Label("Pool Size")
    @Description("The number of connections in the pool when this event begun")
    public final int poolSize;

    public ConnectionBorrowedEvent(boolean stolen, int poolSize) {
        this.stolen = stolen;
        this.poolSize = poolSize;
    }
}