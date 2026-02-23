package ti4.spring.api.dashboard;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.model.EventModel;

@RequiredArgsConstructor
@Service
/**
 * Assembles the API payload for the authenticated player's dashboard.
 *
 * <p>This service composes gameplay summaries and requests aggregate data from
 * {@link PlayerAggregatesService}. Aggregate computation is cache-backed and async; when a cache
 * refresh is in flight, responses include an empty aggregate shell rather than stale aggregate data.
 */
class DashboardService {

    private final PlayerAggregatesService playerAggregatesService;

    /**
     * Builds the full dashboard response for a player.
     *
     * <p>If no managed player exists, returns an empty dashboard shape.
     */
    PlayerDashboardResponse getDashboard(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) {
            return new PlayerDashboardResponse(
                    new PlayerDashboardResponse.PlayerProfile(
                            userId,
                            null,
                            null,
                            null,
                            new PlayerDashboardResponse.TitleSummary(0, List.of()),
                            new PlayerDashboardResponse.DiceLuckSummary(0, 0, null),
                            new PlayerDashboardResponse.PlayerInsights(
                                    new PlayerDashboardResponse.PlayerActivity(0, null),
                                    List.of(),
                                    new PlayerDashboardResponse.ImperialDoctrine("Balanced High Command", List.of()),
                                    List.of()),
                            new PlayerDashboardResponse.PlayerAggregates(
                                    false,
                                    "",
                                    0,
                                    0,
                                    2,
                                    null,
                                    List.of(),
                                    new PlayerDashboardResponse.TechStats(Map.of()),
                                    new PlayerDashboardResponse.FactionWinStats(Map.of()))),
                    new PlayerDashboardResponse.DashboardSummary(0, 0, 0, 0, 0, null),
                    List.of());
        }

        List<ManagedGame> playerGames = managedPlayer.getGames().stream()
                .filter(game -> isRealParticipant(game, userId))
                .sorted(Comparator.comparingLong(ManagedGame::getLastModifiedDate)
                        .reversed())
                .toList();

        String userName = managedPlayer.getName();
        String latestTiglRank =
                getLatestTiglRankAtGameStart(userId, playerGames).orElse(null);

        PlayerDashboardResponse.TitleSummary titleSummary = getTitleSummary(userId, playerGames);
        PlayerDashboardResponse.DiceLuckSummary diceLuckSummary = getDiceLuckSummary(userId, playerGames);

        int gamesPlayed = playerGames.size();
        int activeGames =
                (int) playerGames.stream().filter(ManagedGame::isActive).count();
        int finishedGames =
                (int) playerGames.stream().filter(ManagedGame::isHasEnded).count();
        int abandonedGames = (int) playerGames.stream()
                .filter(game -> game.isHasEnded() && !game.isHasWinner())
                .count();
        int wins = (int) playerGames.stream()
                .map(ManagedGame::getGame)
                .filter(Objects::nonNull)
                .filter(game -> game.isHasEnded() && game.hasWinner())
                .filter(game -> game.getWinners().stream().anyMatch(winner -> userId.equals(winner.getUserID())))
                .count();

        int finishedWithWinner = (int) playerGames.stream()
                .filter(game -> game.isHasEnded() && game.isHasWinner())
                .count();
        Double winPercent = finishedWithWinner == 0 ? null : (wins * 100.0) / finishedWithWinner;

        List<PlayerDashboardResponse.DashboardGame> games = playerGames.stream()
                .map(managedGame -> toDashboardGame(managedGame, userId))
                .filter(Objects::nonNull)
                .toList();

        PlayerDashboardResponse.DashboardSummary dashboardSummary = new PlayerDashboardResponse.DashboardSummary(
                gamesPlayed, activeGames, finishedGames, abandonedGames, wins, winPercent);
        PlayerDashboardResponse.PlayerInsights insights = DashboardBadgeEngine.analyze(
                userId, playerGames, games, dashboardSummary, titleSummary, diceLuckSummary);
        PlayerDashboardResponse.PlayerAggregates aggregates =
                playerAggregatesService.getOrQueueRefresh(userId, playerGames);

        return new PlayerDashboardResponse(
                new PlayerDashboardResponse.PlayerProfile(
                        userId, userName, null, latestTiglRank, titleSummary, diceLuckSummary, insights, aggregates),
                dashboardSummary,
                games);
    }

    private static boolean isRealParticipant(ManagedGame managedGame, String userId) {
        return managedGame.getRealPlayers().stream().anyMatch(player -> userId.equals(player.getId()));
    }

    private static Optional<String> getLatestTiglRankAtGameStart(String userId, List<ManagedGame> playerGames) {
        return playerGames.stream()
                .map(ManagedGame::getGame)
                .filter(Objects::nonNull)
                .map(game -> game.getPlayer(userId))
                .filter(Objects::nonNull)
                .map(Player::getPlayerTIGLRankAtGameStart)
                .filter(Objects::nonNull)
                .map(rank -> rank.getName())
                .findFirst();
    }

    private static PlayerDashboardResponse.TitleSummary getTitleSummary(String userId, List<ManagedGame> playerGames) {
        record TitleBuilder(int count, Set<String> gameIds) {}
        Map<String, TitleBuilder> titleToData = new java.util.HashMap<>();

        for (ManagedGame managedGame : playerGames) {
            if (!managedGame.isHasEnded()) {
                continue;
            }

            Game game = managedGame.getGame();
            if (game == null) {
                continue;
            }

            String stored = game.getStoredValue("TitlesFor" + userId);
            if (stored.isEmpty()) {
                continue;
            }

            Arrays.stream(stored.split("_"))
                    .map(String::trim)
                    .filter(title -> !title.isEmpty() && !"**".equals(title))
                    .forEach(title -> {
                        TitleBuilder current = titleToData.get(title);
                        if (current == null) {
                            LinkedHashSet<String> gameIds = new LinkedHashSet<>();
                            gameIds.add(game.getName());
                            titleToData.put(title, new TitleBuilder(1, gameIds));
                            return;
                        }
                        LinkedHashSet<String> gameIds = new LinkedHashSet<>(current.gameIds());
                        gameIds.add(game.getName());
                        titleToData.put(title, new TitleBuilder(current.count() + 1, gameIds));
                    });
        }

        List<PlayerDashboardResponse.TitleItem> titleItems = titleToData.entrySet().stream()
                .map(entry -> new PlayerDashboardResponse.TitleItem(
                        entry.getKey(),
                        entry.getValue().count(),
                        List.copyOf(entry.getValue().gameIds())))
                .sorted(Comparator.comparingInt(PlayerDashboardResponse.TitleItem::count)
                        .reversed()
                        .thenComparing(PlayerDashboardResponse.TitleItem::title))
                .toList();

        int totalCount = titleItems.stream()
                .mapToInt(PlayerDashboardResponse.TitleItem::count)
                .sum();
        return new PlayerDashboardResponse.TitleSummary(totalCount, titleItems);
    }

    private static PlayerDashboardResponse.DiceLuckSummary getDiceLuckSummary(
            String userId, List<ManagedGame> playerGames) {
        double expectedHits = 0;
        double actualHits = 0;

        for (ManagedGame managedGame : playerGames) {
            Game game = managedGame.getGame();
            if (game == null) {
                continue;
            }

            for (Player player : game.getRealPlayers()) {
                if (!userId.equals(player.getStatsTrackedUserID())) {
                    continue;
                }
                expectedHits += player.getExpectedHitsTimes10() / 10.0;
                actualHits += player.getActualHits();
            }
        }

        Double ratio = expectedHits > 0 ? actualHits / expectedHits : null;
        return new PlayerDashboardResponse.DiceLuckSummary(actualHits, expectedHits, ratio);
    }

    private static PlayerDashboardResponse.DashboardGame toDashboardGame(ManagedGame managedGame, String userId) {
        Game game = managedGame.getGame();
        if (game == null) {
            return null;
        }

        Player you = game.getPlayer(userId);
        if (you == null) {
            return null;
        }

        List<Player> players = game.getRealAndEliminatedPlayers();
        Set<String> winnerUserIds =
                game.getWinners().stream().map(Player::getUserID).collect(Collectors.toSet());

        List<PlayerDashboardResponse.GameParticipant> participants = players.stream()
                .map(player -> new PlayerDashboardResponse.GameParticipant(
                        player.getUserID(),
                        player.getUserName(),
                        cleanValue(player.getFaction()),
                        cleanValue(player.getColor()),
                        player.getTotalVictoryPoints(),
                        player.isEliminated(),
                        winnerUserIds.contains(player.getUserID())))
                .toList();

        List<String> participatingFactionIds = participants.stream()
                .map(PlayerDashboardResponse.GameParticipant::faction)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        List<String> participatingPlayerNames = participants.stream()
                .map(PlayerDashboardResponse.GameParticipant::userName)
                .filter(StringUtils::isNotBlank)
                .toList();

        String modeText = game.getGameModesText();
        List<String> gameModes = StringUtils.isBlank(modeText)
                ? List.of()
                : Arrays.stream(modeText.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .toList();

        String status = getStatus(managedGame);
        boolean isActive = managedGame.isActive();
        boolean isFinished = managedGame.isHasEnded();
        boolean isAbandoned = managedGame.isHasEnded() && !managedGame.isHasWinner();

        String eventDeckId = cleanValue(game.getEventDeckID());
        List<PlayerDashboardResponse.GalacticEvent> eventsInEffect = game.getEventsInEffect().entrySet().stream()
                .map(entry -> toGalacticEvent(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(
                        PlayerDashboardResponse.GalacticEvent::instanceId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        PlayerDashboardResponse.GamePacks packs = new PlayerDashboardResponse.GamePacks(
                game.isBaseGameMode(),
                game.isProphecyOfKings(),
                game.isDiscordantStarsMode(),
                game.isThundersEdge(),
                game.isThundersEdgeDemo(),
                game.isTwilightsFallMode(),
                game.isAbsolMode(),
                game.isMiltyModMode(),
                game.isFrankenGame(),
                game.isVotcMode());

        Double yourDiceLuckRatio =
                you.getExpectedHitsTimes10() > 0 ? you.getActualHits() / (you.getExpectedHitsTimes10() / 10.0) : null;

        PlayerDashboardResponse.YourSeat yourSeat = new PlayerDashboardResponse.YourSeat(
                you.getUserID(),
                you.getUserName(),
                cleanValue(you.getFaction()),
                cleanValue(you.getColor()),
                you.getTotalVictoryPoints(),
                you.isEliminated(),
                you.isPassed(),
                winnerUserIds.contains(userId),
                userId.equals(game.getActivePlayerID()) && !game.isHasEnded(),
                you.getPlayerTIGLRankAtGameStart() == null
                        ? null
                        : you.getPlayerTIGLRankAtGameStart().getName(),
                new PlayerDashboardResponse.DiceLuckSummary(
                        you.getActualHits(), you.getExpectedHitsTimes10() / 10.0, yourDiceLuckRatio));

        String title = StringUtils.isBlank(game.getCustomName()) ? game.getName() : game.getCustomName();
        Long endedAtEpochMs = game.isHasEnded() ? game.getEndedDate() : null;
        String minTiglRank = game.getMinimumTIGLRankAtGameStart() == null
                ? null
                : game.getMinimumTIGLRankAtGameStart().getName();

        return new PlayerDashboardResponse.DashboardGame(
                game.getName(),
                title,
                status,
                isActive,
                isFinished,
                isAbandoned,
                game.getCreationDateTime(),
                game.getLastModifiedDate(),
                endedAtEpochMs,
                game.getVp(),
                game.getRound(),
                game.isCompetitiveTIGLGame(),
                minTiglRank,
                packs,
                gameModes,
                new PlayerDashboardResponse.GalacticEvents(eventDeckId, eventsInEffect),
                yourSeat,
                participatingFactionIds,
                participatingPlayerNames,
                participants,
                game.getActionsChannel() == null
                        ? null
                        : game.getActionsChannel().getJumpUrl(),
                game.getTableTalkChannel() == null
                        ? null
                        : game.getTableTalkChannel().getJumpUrl());
    }

    private static PlayerDashboardResponse.GalacticEvent toGalacticEvent(String eventId, Integer instanceId) {
        EventModel eventModel = Mapper.getEvent(eventId);
        String name = eventModel == null ? eventId : eventModel.getName();
        return new PlayerDashboardResponse.GalacticEvent(eventId, name, instanceId);
    }

    private static String getStatus(ManagedGame game) {
        if (game.isHasEnded() && !game.isHasWinner()) {
            return "ABANDONED";
        }
        if (game.isHasEnded()) {
            return "FINISHED";
        }
        if (game.isActive()) {
            return "ACTIVE";
        }
        return "INACTIVE";
    }

    private static String cleanValue(String value) {
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
