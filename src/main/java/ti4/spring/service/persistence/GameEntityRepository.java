package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEntityRepository extends JpaRepository<GameEntity, String> {
    List<GameEntity> findByTwilightImperiumGlobalLeagueTrueAndEndedEpochMillisecondsIsNull();
}
