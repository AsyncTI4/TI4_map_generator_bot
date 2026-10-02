package ti4.spring.service.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameDatabaseSyncVersion;
import ti4.helpers.TIGLHelper;
import ti4.service.map.FractureService;

@UtilityClass
public class GameEntityMapper {

    static final String UNKNOWN_USER_PREFIX = "UNKNOWN USER ";
    private static final String TITLES_KEY_PREFIX = "TitlesFor";
    private static final int MINIMUM_PERSISTED_PLAYER_COUNT = 3;

    public static boolean shouldPersist(Game game) {
        return game.getRealAndEliminatedPlayers().size() >= MINIMUM_PERSISTED_PLAYER_COUNT;
    }

    public static GameEntitySnapshot toSnapshot(Game game) {
        Map<String, UserEntity> users = new LinkedHashMap<>();
        GameEntity gameEntity = toGameEntity(game, users);
        List<TitleEntity> titles = toTitleEntities(game, gameEntity, users);
        return new GameEntitySnapshot(gameEntity, List.copyOf(users.values()), titles);
    }

    static boolean hasUnknownName(UserEntity user) {
        return user.getName().startsWith(UNKNOWN_USER_PREFIX);
    }

    private static GameEntity toGameEntity(Game game, Map<String, UserEntity> users) {
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
        gameEntity.setTwilightsFall(game.isTwilightsFallMode());
        gameEntity.setPlayerCount(game.getRealAndEliminatedPlayers().size());
        gameEntity.setSyncVersion(syncVersion(game));

        for (Player player : game.getRealAndEliminatedPlayers()) {
            gameEntity.getPlayers().add(toPlayerEntity(player, gameEntity, users));
        }
        return gameEntity;
    }

    public static long syncVersion(Game game) {
        long version = game.getDatabaseSyncVersion();
        return version == 0 ? GameDatabaseSyncVersion.next() : version;
    }

    private static Long getEndedDate(Game game) {
        long endedDate = game.getEndedDate();
        return endedDate == 0 ? null : endedDate;
    }

    private static PlayerEntity toPlayerEntity(Player player, GameEntity gameEntity, Map<String, UserEntity> users) {
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
        playerEntity.setUser(users.computeIfAbsent(
                player.getStatsTrackedUserID(), userId -> toUserEntity(userId, statsTrackedUserName(player))));
        return playerEntity;
    }

    private static String statsTrackedUserName(Player player) {
        if (Objects.equals(player.getUserID(), player.getStatsTrackedUserID())) return player.getUserName();
        return player.getStatsTrackedUserName();
    }

    private static UserEntity lookUpUser(String userId) {
        return toUserEntity(userId, JdaService.getUsername(userId));
    }

    private static UserEntity toUserEntity(String userId, String username) {
        if (StringUtils.isBlank(username)) return new UserEntity(userId, UNKNOWN_USER_PREFIX + userId);
        return new UserEntity(userId, username);
    }

    private static List<TitleEntity> toTitleEntities(Game game, GameEntity gameEntity, Map<String, UserEntity> users) {
        List<TitleEntity> titles = new ArrayList<>();
        for (String storedValueKey : game.getStoredValueMap().keySet()) {
            if (!storedValueKey.startsWith(TITLES_KEY_PREFIX)) continue;

            String userId = storedValueKey.substring(TITLES_KEY_PREFIX.length());
            UserEntity user = users.computeIfAbsent(userId, GameEntityMapper::lookUpUser);
            for (String title : game.getStoredValue(storedValueKey).split("_")) {
                if (title.isBlank()) continue;
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
