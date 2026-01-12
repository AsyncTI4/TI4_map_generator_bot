package ti4.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.logging.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Scheduler for AI player turns.
 * Uses polling to check for AI turns and executes them with concurrency control.
 */
@Service
@RequiredArgsConstructor
public class AiScheduler {

    private final AiConfig config;
    private final Map<String, AiPlayer> aiPlayers = new ConcurrentHashMap<>();
    private final Map<String, Lock> gameLocks = new ConcurrentHashMap<>();
    private final Map<String, String> lastProcessedPhase = new ConcurrentHashMap<>();

    /**
     * Register an AI player for a game.
     * 
     * @param gameId The game ID
     * @param playerId The player ID (color)
     * @param difficulty The difficulty level
     */
    public void registerAiPlayer(String gameId, String playerId, String difficulty) {
        if (!config.isEnabled()) {
            BotLogger.info("[AI] Cannot register AI player - AI system is disabled");
            return;
        }

        String key = makeKey(gameId, playerId);
        
        try {
            PlayerStrategy strategy = createStrategy(difficulty);
            AiPlayer aiPlayer = new AiPlayer(gameId, playerId, strategy, difficulty);
            aiPlayers.put(key, aiPlayer);
            gameLocks.putIfAbsent(gameId, new ReentrantLock());
            
            BotLogger.info(String.format("[AI] Registered AI player: game=%s, player=%s, difficulty=%s",
                gameId, playerId, difficulty));
        } catch (Exception e) {
            BotLogger.error("[AI ERROR] Failed to register AI player", e);
        }
    }

    /**
     * Unregister an AI player from a game.
     */
    public void unregisterAiPlayer(String gameId, String playerId) {
        String key = makeKey(gameId, playerId);
        AiPlayer removed = aiPlayers.remove(key);
        
        if (removed != null) {
            BotLogger.info(String.format("[AI] Unregistered AI player: game=%s, player=%s",
                gameId, playerId));
        }
        
        // Clean up game lock if no more AI players in this game
        if (aiPlayers.keySet().stream().noneMatch(k -> k.startsWith(gameId + ":"))) {
            gameLocks.remove(gameId);
            lastProcessedPhase.remove(gameId);
        }
    }

    /**
     * Pause an AI player.
     */
    public void pauseAiPlayer(String gameId, String playerId) {
        String key = makeKey(gameId, playerId);
        AiPlayer aiPlayer = aiPlayers.get(key);
        
        if (aiPlayer != null) {
            aiPlayer.setPaused(true);
            BotLogger.info(String.format("[AI] Paused AI player: game=%s, player=%s", gameId, playerId));
        }
    }

    /**
     * Resume an AI player.
     */
    public void resumeAiPlayer(String gameId, String playerId) {
        String key = makeKey(gameId, playerId);
        AiPlayer aiPlayer = aiPlayers.get(key);
        
        if (aiPlayer != null) {
            aiPlayer.setPaused(false);
            BotLogger.info(String.format("[AI] Resumed AI player: game=%s, player=%s", gameId, playerId));
        }
    }

    /**
     * Change the difficulty of an AI player.
     */
    public void changeDifficulty(String gameId, String playerId, String newDifficulty) {
        // Unregister and re-register with new difficulty
        unregisterAiPlayer(gameId, playerId);
        registerAiPlayer(gameId, playerId, newDifficulty);
    }

    /**
     * Get status of an AI player.
     */
    public String getStatus(String gameId, String playerId) {
        String key = makeKey(gameId, playerId);
        AiPlayer aiPlayer = aiPlayers.get(key);
        
        if (aiPlayer == null) {
            return String.format("No AI player found for game=%s, player=%s", gameId, playerId);
        }
        
        return aiPlayer.toString() + String.format(
            " actionsTaken=%d", aiPlayer.getActionsTakenThisPhase());
    }

    /**
     * Polling method to check for AI turns.
     * Runs periodically based on config.schedulerDelay.
     */
    @Scheduled(fixedDelayString = "${ai.scheduler.delay:5000}")
    public void checkForAiTurns() {
        if (!config.isEnabled()) {
            return;
        }

        for (String key : aiPlayers.keySet()) {
            AiPlayer aiPlayer = aiPlayers.get(key);
            if (aiPlayer == null || !aiPlayer.isActive()) {
                continue;
            }

            String gameId = aiPlayer.getGameId();
            Lock lock = gameLocks.get(gameId);
            
            if (lock == null || !lock.tryLock()) {
                continue; // Skip if can't acquire lock
            }

            try {
                processAiTurn(aiPlayer);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Process a single AI turn if conditions are met.
     */
    private void processAiTurn(AiPlayer aiPlayer) {
        try {
            String gameId = aiPlayer.getGameId();
            
            if (!GameManager.isValid(gameId)) {
                return;
            }

            Game game = GameManager.getManagedGame(gameId).getGame();
            Player player = game.getPlayer(aiPlayer.getPlayerId());
            
            if (player == null) {
                BotLogger.info("[AI] Player not found in game: " + aiPlayer.getPlayerId());
                return;
            }

            // Check if it's this AI's turn
            if (!isAiTurn(game, player)) {
                return;
            }

            // Check for phase change
            String currentPhase = game.getPhaseOfGame();
            String lastPhase = lastProcessedPhase.get(gameId);
            
            if (lastPhase == null || !lastPhase.equals(currentPhase)) {
                lastProcessedPhase.put(gameId, currentPhase);
                AiTurnRunner runner = new AiTurnRunner(config, aiPlayer);
                runner.onPhaseChange(currentPhase);
            }

            // Execute the turn
            AiTurnRunner runner = new AiTurnRunner(config, aiPlayer);
            boolean actionTaken = runner.executeTurn(game, player);

            if (config.isVerboseLogging()) {
                BotLogger.info(String.format("[AI] Turn processed: game=%s, player=%s, actionTaken=%s",
                    gameId, aiPlayer.getPlayerId(), actionTaken));
            }

            // Save game state if action was taken
            if (actionTaken && !config.isDryRun()) {
                GameManager.save(game, "AI action by " + player.getColor());
            }

        } catch (Exception e) {
            aiPlayer.logError("processAiTurn", e);
        }
    }

    /**
     * Check if it's currently the AI player's turn.
     */
    private boolean isAiTurn(Game game, Player player) {
        // Simple check: is it action phase and player hasn't passed?
        String phase = game.getPhaseOfGame().toLowerCase();
        
        return switch (phase) {
            case "action" -> !player.isPassed() && game.getActivePlayerID() != null 
                && game.getActivePlayerID().equals(player.getUserID());
            case "strategy" -> player.getSCs() == null || player.getSCs().isEmpty();
            case "agenda" -> true; // All players can vote
            case "status" -> true; // All players can score
            default -> false;
        };
    }

    /**
     * Create a strategy instance based on difficulty.
     */
    private PlayerStrategy createStrategy(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "simple" -> new SimpleHeuristicStrategy();
            // Future: case "medium" -> new MediumStrategy();
            // Future: case "hard" -> new HardStrategy();
            default -> new SimpleHeuristicStrategy();
        };
    }

    /**
     * Create a composite key for the AI player map.
     */
    private String makeKey(String gameId, String playerId) {
        return gameId + ":" + playerId;
    }

    /**
     * Get count of registered AI players.
     */
    public int getAiPlayerCount() {
        return aiPlayers.size();
    }

    /**
     * Check if a specific player is AI-controlled.
     */
    public boolean isAiPlayer(String gameId, String playerId) {
        return aiPlayers.containsKey(makeKey(gameId, playerId));
    }
}
