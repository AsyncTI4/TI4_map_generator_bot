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
import ti4.game.persistence.GameFileStamp;
import ti4.game.persistence.ManagedGameState;
import ti4.helpers.TIGLHelper;
import ti4.service.map.FractureService;

@UtilityClass
public class GameEntityMapper {

    static final String UNKNOWN_USER_PREFIX = "UNKNOWN USER ";
    private static final String TITLES_KEY_PREFIX = "TitlesFor";
    private static final int MINIMUM_STATISTICS_PLAYER_COUNT = 3;

    public static GameEntitySnapshot toSnapshot(Game game, GameFileStamp gameFileStamp) {
        return toSnapshot(game, ManagedGameState.of(game), gameFileStamp);
    }

    public static GameEntitySnapshot toSnapshot(
            Game game, ManagedGameState managedGameState, GameFileStamp gameFileStamp) {
        Map<String, UserEntity> users = new LinkedHashMap<>();
        GameEntity gameEntity = toGameEntity(game, managedGameState, gameFileStamp);
        if (gameEntity.isStatisticsIgnored()) {
            return new GameEntitySnapshot(gameEntity, List.of(), List.of());
        }
        for (Player player : game.getRealAndEliminatedPlayers()) {
            gameEntity.getPlayers().add(toPlayerEntity(player, gameEntity, users));
        }
        List<TitleEntity> titles = toTitleEntities(game, gameEntity, users);
        return new GameEntitySnapshot(gameEntity, List.copyOf(users.values()), titles);
    }

    public static PersistedManagedGame toPersistedManagedGame(
            GameEntity gameEntity, List<ManagedGameState.Participant> participants) {
        var state = new ManagedGameState(
                gameEntity.getGameName(),
                gameEntity.isEnded(),
                gameEntity.isWinner(),
                gameEntity.isVictoryPointGoalReached(),
                gameEntity.isFogOfWarMode(),
                gameEntity.isFactionReactMode(),
                gameEntity.isTwilightsFall(),
                gameEntity.isColorReactMode(),
                gameEntity.isStrategyCardReactMode(),
                gameEntity.isFastStrategyCardFollowMode(),
                gameEntity.isFogQol01(),
                gameEntity.isInjectRulesLinks(),
                gameEntity.getCreationEpochMilliseconds(),
                gameEntity.getLastModifiedEpochMilliseconds(),
                gameEntity.getActivePlayerUserId(),
                gameEntity.getLastActivePlayerChangeEpochMilliseconds(),
                Objects.requireNonNullElse(gameEntity.getEndedEpochMilliseconds(), 0L),
                gameEntity.getRound(),
                gameEntity.getGuildId(),
                gameEntity.getMainGameChannelId(),
                gameEntity.getTableTalkChannelId(),
                gameEntity.getLaunchPostThreadId(),
                participants);
        var gameFileStamp =
                new GameFileStamp(gameEntity.getGameFileModifiedEpochMilliseconds(), gameEntity.getGameFileSizeBytes());
        return new PersistedManagedGame(state, gameFileStamp);
    }

    static boolean hasUnknownName(UserEntity user) {
        return user.getName().startsWith(UNKNOWN_USER_PREFIX);
    }

    private static boolean countsForStatistics(Game game) {
        return game.getRealAndEliminatedPlayers().size() >= MINIMUM_STATISTICS_PLAYER_COUNT;
    }

    private static GameEntity toGameEntity(Game game, ManagedGameState managedGameState, GameFileStamp gameFileStamp) {
        var gameEntity = new GameEntity();
        gameEntity.setGameName(game.getName());
        gameEntity.setRound(managedGameState.round());
        gameEntity.setVictoryPointGoal(game.getVp());
        gameEntity.setCreationEpochMilliseconds(managedGameState.creationDateTime());
        gameEntity.setEndedEpochMilliseconds(toNullableEpochMilliseconds(managedGameState.endedDate()));
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
        gameEntity.setTwilightsFall(managedGameState.twilightsFallMode());
        gameEntity.setPlayerCount(game.getRealAndEliminatedPlayers().size());
        gameEntity.setStatisticsIgnored(!countsForStatistics(game));
        gameEntity.setGameFileModifiedEpochMilliseconds(gameFileStamp.lastModifiedEpochMilliseconds());
        gameEntity.setGameFileSizeBytes(gameFileStamp.sizeBytes());
        setManagedGameColumns(gameEntity, managedGameState);
        return gameEntity;
    }

    private static void setManagedGameColumns(GameEntity gameEntity, ManagedGameState managedGameState) {
        gameEntity.setEnded(managedGameState.hasEnded());
        gameEntity.setWinner(managedGameState.hasWinner());
        gameEntity.setVictoryPointGoalReached(managedGameState.vpGoalReached());
        gameEntity.setFogOfWarMode(managedGameState.fowMode());
        gameEntity.setFogQol01(managedGameState.fogQol01());
        gameEntity.setFactionReactMode(managedGameState.factionReactMode());
        gameEntity.setColorReactMode(managedGameState.colorReactMode());
        gameEntity.setStrategyCardReactMode(managedGameState.stratReactMode());
        gameEntity.setFastStrategyCardFollowMode(managedGameState.fastScFollowMode());
        gameEntity.setInjectRulesLinks(managedGameState.injectRules());
        gameEntity.setLastModifiedEpochMilliseconds(managedGameState.lastModifiedDate());
        gameEntity.setActivePlayerUserId(managedGameState.activePlayerId());
        gameEntity.setLastActivePlayerChangeEpochMilliseconds(managedGameState.lastActivePlayerChange());
        gameEntity.setGuildId(managedGameState.guildId());
        gameEntity.setMainGameChannelId(managedGameState.mainGameChannelId());
        gameEntity.setTableTalkChannelId(managedGameState.tableTalkChannelId());
        gameEntity.setLaunchPostThreadId(managedGameState.launchPostThreadId());
        for (ManagedGameState.Participant participant : managedGameState.participants()) {
            gameEntity.getParticipants().add(toParticipantEntity(participant, gameEntity));
        }
    }

    private static GameParticipantEntity toParticipantEntity(
            ManagedGameState.Participant participant, GameEntity gameEntity) {
        var participantEntity = new GameParticipantEntity();
        participantEntity.setGame(gameEntity);
        participantEntity.setUserId(participant.userId());
        participantEntity.setUserName(participant.userName());
        participantEntity.setRealPlayer(participant.realPlayer());
        return participantEntity;
    }

    private static Long toNullableEpochMilliseconds(long epochMilliseconds) {
        return epochMilliseconds == 0 ? null : epochMilliseconds;
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
