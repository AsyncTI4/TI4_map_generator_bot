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
                    "INSERT INTO interaction_count (type, name, count) VALUES (:type, :name, 1) ON CONFLICT(type, name) DO UPDATE SET count = count + 1",
            nativeQuery = true)
    void incrementCount(@Param("type") String type, @Param("name") String name);

    List<InteractionCountEntity> findAllByType(String type);

    @Query("SELECT SUM(e.count) FROM InteractionCountEntity e WHERE e.type = :type")
    Long sumCountByType(@Param("type") String type);
}
