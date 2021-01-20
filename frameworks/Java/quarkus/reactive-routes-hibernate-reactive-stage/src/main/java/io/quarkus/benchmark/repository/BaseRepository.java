package io.quarkus.benchmark.repository;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.inject.Inject;

import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.stage.Stage;

import io.smallrye.mutiny.Uni;

public class BaseRepository {

    @Inject
    protected Stage.SessionFactory sf;

    public <T> CompletionStage<T> inSession(Function<Stage.Session, CompletionStage<T>> work) {
        return sf.withSession( session -> work.apply( session ) );
    }
}
