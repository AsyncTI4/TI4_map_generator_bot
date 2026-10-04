package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StandaloneTitleEntityRepository extends JpaRepository<StandaloneTitleEntity, Long> {

    @Query("SELECT new ti4.spring.service.persistence.EarnedTitle(t.title, t.source) FROM StandaloneTitleEntity t"
            + " WHERE t.user.id = :userId")
    List<EarnedTitle> findEarnedTitlesByUserId(@Param("userId") String userId);
}
