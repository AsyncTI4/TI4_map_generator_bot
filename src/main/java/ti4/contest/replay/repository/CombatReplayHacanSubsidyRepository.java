package ti4.contest.replay.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayHacanSubsidyEntity;

public interface CombatReplayHacanSubsidyRepository extends JpaRepository<CombatReplayHacanSubsidyEntity, Long> {

    List<CombatReplayHacanSubsidyEntity> findByContestId(Long contestId);

    void deleteByContestId(Long contestId);
}
