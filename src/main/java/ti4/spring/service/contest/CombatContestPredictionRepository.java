package ti4.spring.service.contest;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CombatContestPredictionRepository extends JpaRepository<CombatContestPredictionEntity, Long> {

    List<CombatContestPredictionEntity> findByContestId(Long contestId);

    List<CombatContestPredictionEntity> findByPointsAwardedIsNotNull();

    @Query("""
            select p.discordUserId as discordUserId,
                   case when sum(p.pointsAwarded) < 0 then 0 else sum(p.pointsAwarded) end as totalPoints
            from CombatContestPredictionEntity p
            where p.pointsAwarded is not null
              and p.discordUserId in :discordUserIds
            group by p.discordUserId
            """)
    List<CombatContestUserPointsRow> findPointTotalsByDiscordUserIdIn(Collection<String> discordUserIds);

    @Query("""
            select p.discordUserId as discordUserId,
                   max(p.discordUserName) as discordUserName,
                   case when sum(p.pointsAwarded) < 0 then 0 else sum(p.pointsAwarded) end as totalPoints,
                   count(p.id) as predictionCount,
                   sum(case when p.correct = true then 1 else 0 end) as correctPredictions
            from CombatContestPredictionEntity p
            where p.pointsAwarded is not null
            group by p.discordUserId
            order by case when sum(p.pointsAwarded) < 0 then 0 else sum(p.pointsAwarded) end desc,
                     sum(case when p.correct = true then 1 else 0 end) desc,
                     max(p.discordUserName) asc
            """)
    List<CombatContestLeaderboardRow> findLeaderboardRows(Pageable pageable);
}
