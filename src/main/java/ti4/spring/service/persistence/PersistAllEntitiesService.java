package ti4.spring.service.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.service.map.FractureService;
import ti4.spring.jda.JdaService;
import ti4.spring.persistence.GameEntity;
import ti4.spring.persistence.GameEntityRepository;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.UserEntity;
import ti4.spring.persistence.UserEntityRepository;

@Service
@RequiredArgsConstructor
public class PersistAllEntitiesService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final UserEntityRepository userEntityRepository;

    @Transactional
    public void persistAll() {
        Map<String, UserEntity> userCache = persistAllUsers();
        persistAllGames(userCache);
    }

    private Map<String, UserEntity> persistAllUsers() {
        BotLogger.info("Starting persistAllUsers.");
        userEntityRepository.deleteAllInBatch();
        BotLogger.info("Deleted all persisted users.");

        List<UserEntity> userEntities = GameManager.getManagedPlayers().stream()
                .map(player -> new UserEntity(player.getId(), player.getName()))
                .toList();

        List<UserEntity> savedUsers = userEntityRepository.saveAll(userEntities);

        BotLogger.info(String.format("Persisted %,d user rows.", savedUsers.size()));

        return savedUsers.stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u, (existing, replacement) -> existing));
    }

    private void persistAllGames(Map<String, UserEntity> userCache) {
        BotLogger.info("Starting persistAllGames.");
        playerEntityRepository.deleteAllInBatch();
        gameEntityRepository.deleteAllInBatch();
        BotLogger.info("Deleted all persisted games.");

        List<GameEntity> gameEntities = new ArrayList<>();
        for (ManagedGame managedGame : GameManager.getManagedGames()) {
            Game game = managedGame.getGame();
            gameEntities.add(toEntity(game, userCache));
        }

        gameEntityRepository.saveAll(gameEntities);
        BotLogger.info(String.format("Persisted %,d game rows.", gameEntities.size()));
    }

    private GameEntity toEntity(Game game, Map<String, UserEntity> userCache) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(game.getName());
        gameEntity.setRound(game.getRound());
        gameEntity.setCreationEpochMilliseconds(game.getCreationDateTime());
        gameEntity.setEndedEpochMilliseconds(game.getEndedDate());
        gameEntity.setCompleted(game.getWinner().isPresent() && game.isHasEnded());
        gameEntity.setFractureInPlay(FractureService.isFractureInPlay(game));
        gameEntity.setHomebrew(game.isHomebrew());
        gameEntity.setDiscordantStarsMode(game.isDiscordantStarsMode());
        gameEntity.setAbsolMode(game.isAbsolMode());
        gameEntity.setFrankenMode(game.isFrankenGame());
        gameEntity.setAllianceMode(game.isAllianceMode());
        gameEntity.setTwilightImperiumGlobalLeague(game.isCompetitiveTIGLGame());
        gameEntity.setProphecyOfKings(game.isProphecyOfKings());
        gameEntity.setThundersEdge(game.isThundersEdge());

        var players = gameEntity.getPlayers();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            var playerEntity = toEntity(player, gameEntity, userCache);
            players.add(playerEntity);
        }

        return gameEntity;
    }

    private PlayerEntity toEntity(Player player, GameEntity gameEntity, Map<String, UserEntity> userCache) {
        var playerEntity = new PlayerEntity();

        playerEntity.setUsername(player.getUserName());
        playerEntity.setFactionName(player.getFaction());
        playerEntity.setScore(player.getTotalVictoryPoints());
        playerEntity.setTotalNumberOfTurns(player.getNumberOfTurns());
        playerEntity.setTotalTurnTime(player.getTotalTurnTime());
        playerEntity.setExpectedHits(player.getExpectedHits());
        playerEntity.setActualHits(player.getActualHits());
        playerEntity.setEliminated(player.isEliminated());
        playerEntity.setWinner(player.getGame().getWinners().contains(player));

        playerEntity.setGame(gameEntity);

        var userEntity = userCache.computeIfAbsent(player.getStatsTrackedUserID(), userId -> createUserEntity(player));
        playerEntity.setUser(userEntity);

        return playerEntity;
    }

    private UserEntity createUserEntity(Player player) {
        String statsTrackedUserId = player.getStatsTrackedUserID();
        if (Objects.equals(statsTrackedUserId, player.getUserID())) {
            return new UserEntity(player.getUserID(), player.getUserName());
        }
        String username = JdaService.getUsername(statsTrackedUserId);
        if (username == null) username = "UNKNOWN USER " + statsTrackedUserId;
        var userEntity = new UserEntity(statsTrackedUserId, username);
        return userEntityRepository.save(userEntity);
    }
}
