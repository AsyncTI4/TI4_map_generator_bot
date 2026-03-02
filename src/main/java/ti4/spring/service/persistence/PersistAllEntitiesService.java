package ti4.spring.service.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.service.map.FractureService;
import ti4.spring.jda.JdaService;

@Service
@RequiredArgsConstructor
public class PersistAllEntitiesService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final TitleEntityRepository titleEntityRepository;
    private final UserEntityRepository userEntityRepository;

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
        titleEntityRepository.deleteAllInBatch();
        playerEntityRepository.deleteAllInBatch();
        gameEntityRepository.deleteAllInBatch();
        BotLogger.info("Deleted all persisted games.");

        List<GameEntity> gameEntities = new ArrayList<>();
        List<TitleEntity> titleEntities = new ArrayList<>();
        for (ManagedGame managedGame : GameManager.getManagedGames()) {
            Game game = managedGame.getGame();
            var gameEntity = toEntity(game, userCache);
            gameEntities.add(gameEntity);
            titleEntities.addAll(toTitleEntities(game, gameEntity, userCache));
        }

        gameEntityRepository.saveAll(gameEntities);
        titleEntityRepository.saveAll(titleEntities);
        BotLogger.info(String.format("Persisted %,d game rows.", gameEntities.size()));
        BotLogger.info(String.format("Persisted %,d title rows.", titleEntities.size()));
    }

    private GameEntity toEntity(Game game, Map<String, UserEntity> userCache) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(game.getName());
        gameEntity.setRound(game.getRound());
        gameEntity.setVictoryPointGoal(game.getVp());
        gameEntity.setCreationEpochMilliseconds(game.getCreationDateTime());
        gameEntity.setEndedEpochMilliseconds(getEndedDate(game));
        gameEntity.setHasEnded(game.isHasEnded());
        gameEntity.setCompleted(game.getWinner().isPresent() && game.isHasEnded());
        gameEntity.setFractureInPlay(FractureService.isFractureInPlay(game));
        gameEntity.setHomebrew(game.isHomebrew());
        gameEntity.setDiscordantStarsMode(game.isDiscordantStarsMode());
        gameEntity.setAbsolMode(game.isAbsolMode());
        gameEntity.setFrankenMode(game.isFrankenGame());
        gameEntity.setAllianceMode(game.isAllianceMode());
        gameEntity.setTwilightImperiumGlobalLeague(game.isCompetitiveTIGLGame());
        gameEntity.setTwilightImperiumGlobalLeagueRank(
                game.getMinimumTIGLRankAtGameStart() == null
                        ? null
                        : game.getMinimumTIGLRankAtGameStart().toString());
        gameEntity.setProphecyOfKings(game.isProphecyOfKings());
        gameEntity.setThundersEdge(game.isThundersEdge());
        gameEntity.setPlayerCount(game.getRealAndEliminatedPlayers().size());

        var players = gameEntity.getPlayers();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            var playerEntity = toEntity(player, gameEntity, userCache);
            players.add(playerEntity);
        }

        return gameEntity;
    }

    private Long getEndedDate(Game game) {
        long endedDate = game.getEndedDate();
        return endedDate == 0 ? null : endedDate;
    }

    private PlayerEntity toEntity(Player player, GameEntity gameEntity, Map<String, UserEntity> userCache) {
        var playerEntity = new PlayerEntity();

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

    private List<TitleEntity> toTitleEntities(Game game, GameEntity gameEntity, Map<String, UserEntity> userCache) {
        List<TitleEntity> titles = new ArrayList<>();
        for (String storedValue : game.getMessagesThatICheckedForAllReacts().keySet()) {
            if (!storedValue.startsWith("TitlesFor")) {
                continue;
            }

            String userId = storedValue.substring("TitlesFor".length());
            UserEntity user = userCache.get(userId);
            if (user == null) {
                continue;
            }

            String storedTitles = game.getStoredValue(storedValue);
            for (String title : storedTitles.split("_")) {
                if (title.isBlank()) {
                    continue;
                }
                var titleEntity = new TitleEntity();
                titleEntity.setGame(gameEntity);
                titleEntity.setUser(user);
                titleEntity.setTitle(title);
                titles.add(titleEntity);
            }
        }
        return titles;
    }
}
