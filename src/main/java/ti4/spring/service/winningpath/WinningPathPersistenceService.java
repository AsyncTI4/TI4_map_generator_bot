package ti4.spring.service.winningpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.logging.BotLogger;
import ti4.service.statistics.game.WinningPathHelper;

@AllArgsConstructor
@Service
public class WinningPathPersistenceService {

    private WinningPathRepository winningPathRepository;

    @Transactional
    public void recompute() {
        BotLogger.info("**Recomputing win paths table**");
        winningPathRepository.deleteAll();
        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getNormalFinishedGamesFilter(null, null), this::addGame);
        BotLogger.info("**Finished recomputing win paths table**");
    }

    @Transactional
    public void addGame(Game game) {
        game.getWinner().ifPresent(winner -> {
            String path = WinningPathHelper.buildWinningPath(game, winner);
            int playerCount = game.getRealAndEliminatedPlayers().size();
            int vp = game.getVp();
            WinningPath winningPath = winningPathRepository.findByPlayerCountAndVictoryPointsAndPath(playerCount, vp, path)
                .map(wp -> wp.setCount(wp.getCount() + 1))
                .orElseGet(() -> new WinningPath(playerCount, vp, path, 1));
            winningPathRepository.save(winningPath);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getWinningPathCounts(int playerCount, int victoryPoints) {
        List<WinningPath> list = winningPathRepository.findByPlayerCountAndVictoryPoints(playerCount, victoryPoints);
        Map<String, Integer> map = new HashMap<>();
        for (WinningPath wp : list) {
            map.put(wp.getPath(), wp.getCount());
        }
        return map;
    }
}