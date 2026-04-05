package ti4.spring.api.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PlayerAggregatesCache} rows.
 *
 * <p>Lookups are always by {@code userId} because each player has one aggregate cache row.
 */
interface PlayerAggregatesCacheRepository extends JpaRepository<PlayerAggregatesCache, String> {}
