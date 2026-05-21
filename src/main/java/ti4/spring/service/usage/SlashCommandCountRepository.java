package ti4.spring.service.usage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlashCommandCountRepository extends JpaRepository<SlashCommandCountEntity, Long> {

    @Modifying
    @Query(
            value =
                    "INSERT INTO slash_command_count (name, date, count) "
                            + "SELECT :name, :date, 1 "
                            + "WHERE NOT EXISTS (SELECT 1 FROM slash_command_count WHERE name = :name AND date = :date)",
            nativeQuery = true)
    int insertCount(@Param("name") String name, @Param("date") String date);

    @Modifying
    @Query(
            value =
                    "UPDATE slash_command_count "
                            + "SET count = count + 1 "
                            + "WHERE id = (SELECT id FROM slash_command_count WHERE name = :name AND date = :date ORDER BY id LIMIT 1)",
            nativeQuery = true)
    int incrementExisting(@Param("name") String name, @Param("date") String date);

    @Query(
            value =
                    "SELECT name, SUM(day_total) AS total "
                            + "FROM (SELECT name, date, MAX(count) AS day_total FROM slash_command_count GROUP BY name, date) grouped_counts "
                            + "GROUP BY name",
            nativeQuery = true)
    List<Object[]> sumAllByName();

    @Query(
            value =
                    "SELECT name, SUM(day_total) AS total "
                            + "FROM (SELECT name, date, MAX(count) AS day_total FROM slash_command_count WHERE date >= :since GROUP BY name, date) grouped_counts "
                            + "GROUP BY name",
            nativeQuery = true)
    List<Object[]> sumByNameSince(@Param("since") String since);

    @Query(value = "SELECT SUM(day_total) FROM (SELECT MAX(count) AS day_total FROM slash_command_count GROUP BY name, date)", nativeQuery = true)
    Long sumAllCounts();

    @Query(
            value =
                    "SELECT SUM(day_total) "
                            + "FROM (SELECT MAX(count) AS day_total FROM slash_command_count WHERE date >= :since GROUP BY name, date)",
            nativeQuery = true)
    Long sumCountsSince(@Param("since") String since);
}
