package io.quarkus.benchmark.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;

import org.hibernate.reactive.stage.Stage;

import io.quarkus.benchmark.model.World;


@Singleton
public class WorldRepository extends BaseRepository {

	/**
	 * This method is not required (nor specified) by the benchmark rules,
	 * but is quite handy to seed a local database and be able to experiment
	 * with the app locally.
	 */
	public CompletionStage<Void> createData() {
		return inSession( s -> {
			final ThreadLocalRandom random = ThreadLocalRandom.current();
			int MAX = 10000;
			CompletableFuture<Void>[] unis = new CompletableFuture[MAX];
			for ( int i = 0; i < MAX; i++ ) {
				final World world = new World();
				world.setId( i + 1 );
				world.setRandomNumber( 1 + random.nextInt( 10000 ) );
				unis[i] = s.persist( world ).toCompletableFuture();
			}
			return CompletableFuture.allOf( unis ).thenCompose( unused -> s.flush() );
		} );
	}

	public CompletionStage<World> find(int id) {
		return inSession( session -> session.find( World.class, id ) );
	}

	public CompletionStage<Collection<World>> update(Stage.Session s, Collection<World> worlds) {
		return s.flush().thenApply( v -> worlds );
	}

	public CompletionStage<Collection<World>> find(Stage.Session s, Set<Integer> ids) {
		//The rules require individual load: we can't use the Hibernate feature which allows load by multiple IDs as one single operation
		List<World> worlds = new ArrayList<>( ids.size() );
		CompletableFuture<Void>[] l = new CompletableFuture[ids.size()];
		int i = 0;
		for ( Integer id : ids ) {
			l[i++] = s
					.find( World.class, id )
					.thenAccept( worlds::add )
					.toCompletableFuture();
		}
		return CompletableFuture
				.allOf( l )
				.thenApply( unused -> worlds );
	}
}
