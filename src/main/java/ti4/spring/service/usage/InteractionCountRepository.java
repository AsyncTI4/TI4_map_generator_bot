package ti4.spring.service.usage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InteractionCountRepository extends JpaRepository<InteractionCountEntity, Long> {

    @Modifying
    @Query(
            value =
                    "INSERT INTO slash_command_count (name, date, count) VALUES (:name, :date, 1) ON CONFLICT(name, date) DO UPDATE SET count = count + 1",
            nativeQuery = true)
    void incrementCount(@Param("name") String name, @Param("date") String date);

    @Query(value = "SELECT name, SUM(count) as total FROM slash_command_count GROUP BY name", nativeQuery = true)
    List<Object[]> sumAllByName();

    @Query(
            value = "SELECT name, SUM(count) as total FROM slash_command_count WHERE date >= :since GROUP BY name",
            nativeQuery = true)
    List<Object[]> sumByNameSince(@Param("since") String since);

    @Query(value = "SELECT SUM(count) FROM slash_command_count", nativeQuery = true)
    Long sumAllCounts();

    @Query(value = "SELECT SUM(count) FROM slash_command_count WHERE date >= :since", nativeQuery = true)
    Long sumCountsSince(@Param("since") String since);
}
