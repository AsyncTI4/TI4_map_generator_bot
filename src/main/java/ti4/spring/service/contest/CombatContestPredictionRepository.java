package ti4.spring.service.contest;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CombatContestPredictionRepository extends JpaRepository<CombatContestPredictionEntity, Long> {

    List<CombatContestPredictionEntity> findByContestId(Long contestId);

    List<CombatContestPredictionEntity> findByPointsAwardedIsNotNull();

    @Query("""
            select p.discordUserId as discordUserId,
                   max(p.discordUserName) as discordUserName,
                   sum(p.pointsAwarded) as totalPoints,
                   count(p.id) as predictionCount,
                   sum(case when p.correct = true then 1 else 0 end) as correctPredictions
            from CombatContestPredictionEntity p
            where p.pointsAwarded is not null
            group by p.discordUserId
            order by sum(p.pointsAwarded) desc,
                     sum(case when p.correct = true then 1 else 0 end) desc,
                     max(p.discordUserName) asc
            """)
    List<CombatContestLeaderboardRow> findLeaderboardRows(Pageable pageable);
}
