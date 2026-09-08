package ti4.spring.service.statistics.matchmaking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.service.statistics.game.MatchmakingGameRankEvaluator;

class SimulatedGameRankProvider {

    private final ToDoubleFunction<String> ratingByUserId;
    private final Map<String, Map<String, Integer>> ranksByGameName = new HashMap<>();

    SimulatedGameRankProvider(ToDoubleFunction<String> ratingByUserId) {
        this.ratingByUserId = ratingByUserId;
    }

    Map<String, Integer> getRanks(String gameName) {
        return ranksByGameName.computeIfAbsent(gameName, this::evaluate);
    }

    private Map<String, Integer> evaluate(String gameName) {
        try {
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            if (managedGame == null) {
                return Map.of();
            }
            Game game = managedGame.getGame();
            if (game == null) {
                return Map.of();
            }
            return MatchmakingGameRankEvaluator.evaluate(game, ratingByUserId);
        } catch (Exception e) {
            BotLogger.error("Failed to simulate matchmaking ranks for " + gameName, e);
            return Map.of();
        }
    }
}
