package ti4.spring.service.winningpath;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface WinningPathRepository extends JpaRepository<WinningPath, Long> {

    Optional<WinningPath> findByPlayerCountAndVictoryPointsAndPath(int playerCount, int victoryPoints, String path);

    List<WinningPath> findByPlayerCountAndVictoryPoints(int playerCount, int victoryPoints);

    List<WinningPath> findByPlayerCountAndVictoryPointsAndPathContainsIgnoreCase(int playerCount, int victoryPoints, String path);
}