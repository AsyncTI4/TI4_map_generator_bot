package ti4.spring.service.contest;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombatContestSampleRepository extends JpaRepository<CombatContestSampleEntity, Long> {

    List<CombatContestSampleEntity> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(LocalDateTime startedAt);
}
