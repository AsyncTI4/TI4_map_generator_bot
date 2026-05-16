package ti4.spring.service.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper;
import ti4.service.map.FractureService;

@Service
@RequiredArgsConstructor
public class GameEntitySyncService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final TitleEntityRepository titleEntityRepository;
    private final UserEntityRepository userEntityRepository;

    @Transactional
    public void sync(Game game) {
        Set<String> previouslyReferencedUserIds = findReferencedUserIds(game.getName());
        if (game.getRealAndEliminatedPlayers().size() < 3) {
            deleteGameRows(game.getName());
            deleteOrphanedUsers(previouslyReferencedUserIds);
            return;
        }

        deleteGameRows(game.getName());

        Map<String, UserEntity> userCache = new HashMap<>();
        GameEntity gameEntity = toGameEntity(game, userCache);
        gameEntityRepository.save(gameEntity);

        List<TitleEntity> titleEntities = toTitleEntities(game, gameEntity, userCache);
        if (!titleEntities.isEmpty()) {
            titleEntityRepository.saveAll(titleEntities);
        }

        deleteOrphanedUsers(previouslyReferencedUserIds);
    }

    @Transactional
    public void delete(String gameName) {
        Set<String> previouslyReferencedUserIds = findReferencedUserIds(gameName);
        deleteGameRows(gameName);
        deleteOrphanedUsers(previouslyReferencedUserIds);
    }

    private Set<String> findReferencedUserIds(String gameName) {
        Set<String> userIds = new HashSet<>(playerEntityRepository.findDistinctUserIdsByGameName(gameName));
        userIds.addAll(titleEntityRepository.findDistinctUserIdsByGameName(gameName));
        return userIds;
    }

    private void deleteGameRows(String gameName) {
        titleEntityRepository.deleteByGame_GameName(gameName);
        playerEntityRepository.deleteByGame_GameName(gameName);
        gameEntityRepository.deleteAllByIdInBatch(List.of(gameName));
    }

    private void deleteOrphanedUsers(Set<String> userIds) {
        List<String> orphanedUserIds = userIds.stream().filter(this::isOrphanedUser).toList();
        if (!orphanedUserIds.isEmpty()) {
            userEntityRepository.deleteAllByIdInBatch(orphanedUserIds);
        }
    }

    private boolean isOrphanedUser(String userId) {
        return !playerEntityRepository.existsByUser_Id(userId) && !titleEntityRepository.existsByUser_Id(userId);
    }

    private GameEntity toGameEntity(Game game, Map<String, UserEntity> userCache) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(game.getName());
        gameEntity.setRound(game.getRound());
        gameEntity.setVictoryPointGoal(game.getVp());
        gameEntity.setCreationEpochMilliseconds(game.getCreationDateTime());
        gameEntity.setEndedEpochMilliseconds(getEndedDate(game));
        gameEntity.setCompleted(game.getWinner().isPresent() && game.isHasEnded());
        gameEntity.setFractureInPlay(FractureService.isFractureInPlay(game));
        gameEntity.setHomebrew(game.isHomebrew());
        gameEntity.setDiscordantStarsMode(game.isDiscordantStarsMode());
        gameEntity.setAbsolMode(game.isAbsolMode());
        gameEntity.setFrankenMode(game.isFrankenGame());
        gameEntity.setAllianceMode(game.isAllianceMode());
        gameEntity.setTwilightImperiumGlobalLeague(game.isCompetitiveTIGLGame());
        gameEntity.setTwilightImperiumGlobalLeagueFractured(
                game.isCompetitiveTIGLGame() && TIGLHelper.isFracturedTIGLGame(game));
        gameEntity.setTwilightImperiumGlobalLeagueRank(
                game.getMinimumTIGLRankAtGameStart() == null
                        ? null
                        : game.getMinimumTIGLRankAtGameStart().toString());
        gameEntity.setProphecyOfKings(game.isProphecyOfKings());
        gameEntity.setThundersEdge(game.isThundersEdge());
        gameEntity.setPlayerCount(game.getRealAndEliminatedPlayers().size());

        var players = gameEntity.getPlayers();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            players.add(toPlayerEntity(player, gameEntity, userCache));
        }

        return gameEntity;
    }

    private Long getEndedDate(Game game) {
        long endedDate = game.getEndedDate();
        return endedDate == 0 ? null : endedDate;
    }

    private PlayerEntity toPlayerEntity(Player player, GameEntity gameEntity, Map<String, UserEntity> userCache) {
        var playerEntity = new PlayerEntity();
        playerEntity.setFactionName(player.getFaction());
        playerEntity.setScore(player.getTotalVictoryPoints());
        playerEntity.setTotalNumberOfTurns(player.getNumberOfTurns());
        playerEntity.setTotalTurnTime(player.getTotalTurnTime());
        playerEntity.setExpectedHits(player.getExpectedHits());
        playerEntity.setActualHits(player.getActualHits());
        playerEntity.setEliminated(player.isEliminated());
        playerEntity.setWinner(player.getGame().getWinners().contains(player));
        playerEntity.setReplaced(!Objects.equals(player.getUserID(), player.getStatsTrackedUserID()));
        playerEntity.setGame(gameEntity);
        playerEntity.setUser(userCache.computeIfAbsent(
                player.getStatsTrackedUserID(),
                ignored -> upsertUser(player.getStatsTrackedUserID(), resolvePlayerName(player))));
        return playerEntity;
    }

    private String resolvePlayerName(Player player) {
        if (Objects.equals(player.getStatsTrackedUserID(), player.getUserID())) {
            return player.getUserName();
        }
        if (player.getStatsTrackedUserName() != null) {
            return player.getStatsTrackedUserName();
        }
        return resolveUsername(player.getStatsTrackedUserID());
    }

    private UserEntity upsertUser(String userId, String username) {
        UserEntity userEntity = userEntityRepository.findById(userId).orElseGet(() -> new UserEntity(userId, username));
        userEntity.setName(username);
        return userEntityRepository.save(userEntity);
    }

    private List<TitleEntity> toTitleEntities(Game game, GameEntity gameEntity, Map<String, UserEntity> userCache) {
        List<TitleEntity> titles = new ArrayList<>();
        for (String storedValue : game.getStoredValueMap().keySet()) {
            if (!storedValue.startsWith("TitlesFor")) {
                continue;
            }

            String userId = storedValue.substring("TitlesFor".length());
            UserEntity user = userCache.computeIfAbsent(userId, ignored -> upsertUser(userId, resolveUsername(userId)));

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

    private String resolveUsername(String userId) {
        return userEntityRepository
                .findById(userId)
                .map(UserEntity::getName)
                .orElseGet(() -> {
                    String username = JdaService.getUsername(userId);
                    return username == null ? "UNKNOWN USER " + userId : username;
                });
    }
}
