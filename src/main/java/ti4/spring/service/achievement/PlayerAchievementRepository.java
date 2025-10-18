package ti4.spring.service.achievement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, Long> {
    Optional<PlayerAchievement> findByUserIdAndAchievementKeyAndGameMode(
            String userId, String achievementKey, String gameMode);
}
