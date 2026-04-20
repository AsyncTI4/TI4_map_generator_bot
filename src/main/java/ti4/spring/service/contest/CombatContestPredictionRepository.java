package ti4.spring.service.contest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombatContestPredictionRepository extends JpaRepository<CombatContestPredictionEntity, Long> {

    List<CombatContestPredictionEntity> findByContestId(Long contestId);

    List<CombatContestPredictionEntity> findByPointsAwardedIsNotNull();
}
