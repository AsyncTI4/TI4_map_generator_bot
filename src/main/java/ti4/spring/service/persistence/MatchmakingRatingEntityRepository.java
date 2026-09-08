package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchmakingRatingEntityRepository extends JpaRepository<MatchmakingRatingEntity, Long> {

    List<MatchmakingRatingEntity> findAllByTiglOnly(boolean tiglOnly);
}
