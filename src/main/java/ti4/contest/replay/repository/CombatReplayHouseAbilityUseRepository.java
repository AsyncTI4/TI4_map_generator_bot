package ti4.contest.replay.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;

public interface CombatReplayHouseAbilityUseRepository extends JpaRepository<CombatReplayHouseAbilityUseEntity, Long> {
    boolean existsByCandidateIdAndHouse(Long candidateId, CombatReplayHouse house);

    List<CombatReplayHouseAbilityUseEntity> findByCandidateIdAndHouse(Long candidateId, CombatReplayHouse house);

    List<CombatReplayHouseAbilityUseEntity> findByHouse(CombatReplayHouse house);
}
