package io.quarkus.benchmark.repository;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import io.quarkus.benchmark.utils.LocalRandom;
import io.quarkus.benchmark.utils.Randomizer;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.util.impl.CompletionStages;

import io.quarkus.benchmark.model.World;
import io.smallrye.mutiny.Uni;

import static org.hibernate.reactive.util.impl.CompletionStages.loop;

@Singleton
public class WorldRepository extends BaseRepository {

    /**
     * This method is not required (nor specified) by the benchmark rules,
     * but is quite handy to seed a local database and be able to experiment
     * with the app locally.
     */
    public Uni<Void> createData() {
        return inSession(s -> {
            final LocalRandom random = Randomizer.current();
            int MAX = 10000;
            // Avoid StackOverflowException when creating so many CompletionStage object
            return Uni.createFrom().completionStage( loop( 0, MAX, i -> {
                final World world = new World();
                world.setId( i + 1 );
                world.setRandomNumber( random.getNextRandom() );
                return s.persist( world ).subscribeAsCompletionStage();
            } ) ).call( s::flush );
        });
    }

    public Uni<List<World>> update(Mutiny.Session session, List<World> worlds) {
        return session
                .setBatchSize(worlds.size())
                .flush()
                .map(v -> worlds);
    }

    public Uni<List<World>> findStateless(int count) {
        return inStatelessSession(session -> findStateless(session, count));
    }

    private Uni<List<World>> findStateless(Mutiny.StatelessSession s, int count) {
        //The rules require individual load: we can't use the Hibernate feature which allows load by multiple IDs
        // as one single operation as Hibernate is too smart and will switch to use batched loads automatically.
        // Hence, use this awkward alternative:
        final LocalRandom localRandom = Randomizer.current();
        final List<World> worlds = new ArrayList<>(count);
        Uni<Void> loopRoot = Uni.createFrom().voidItem();
        for (int i = 0; i < count; i++) {
            loopRoot = loopRoot.call(() -> s.get(World.class, localRandom.getNextRandom()).invoke(worlds::add));
        }
        return loopRoot.map(v -> worlds);
    }

    public Uni<List<World>> findManaged(Mutiny.Session s, int count) {
        final List<World> worlds = new ArrayList<>(count);
        //The rules require individual load: we can't use the Hibernate feature which allows load by multiple IDs
        // as one single operation as Hibernate is too smart and will switch to use batched loads.
        // But also, we can't use "Uni#join" as we did in the above method as managed entities shouldn't use pipelining -
        // so we also have to avoid Mutiny optimising things by establishing an explicit chain:
        final LocalRandom localRandom = Randomizer.current();
        Uni<Void> loopRoot = Uni.createFrom().voidItem();
        for (int i = 0; i < count; i++) {
            loopRoot = loopRoot
                    .call(() -> s.find(World.class, localRandom.getNextRandom()).invoke(worlds::add));
        }
        return loopRoot.map(v -> worlds);
    }

    public Uni<World> findStateless() {
        return inStatelessSession(session -> session.get(World.class, Randomizer.current().getNextRandom()));
    }
}
