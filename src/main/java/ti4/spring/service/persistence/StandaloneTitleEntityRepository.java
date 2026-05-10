package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StandaloneTitleEntityRepository extends JpaRepository<StandaloneTitleEntity, Long> {

    @Query("SELECT t FROM StandaloneTitleEntity t JOIN FETCH t.user WHERE t.user.id = :userId")
    List<StandaloneTitleEntity> findByUserIdWithUser(@Param("userId") String userId);
}
