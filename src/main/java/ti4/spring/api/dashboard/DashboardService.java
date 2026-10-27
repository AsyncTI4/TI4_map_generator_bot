package ti4.spring.api.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;
import ti4.model.EventModel;
import ti4.model.Mapper;
import ti4.service.game.ManagedGameService;
import ti4.spring.service.persistence.EarnedTitle;
import ti4.spring.service.title.PlayerTitleService;

@RequiredArgsConstructor
@Service
/*
 Assembles the API payload for the authenticated player's dashboard.

 <p>This service composes gameplay summaries and requests aggregate data from
 {@link PlayerAggregatesService}. Aggregate computation is cache-backed and async; when a cache
 refresh is in flight, responses include an empty aggregate shell rather than stale aggregate data.
*/
class DashboardService {

    private final PlayerAggregatesService playerAggregatesService;
    private final PlayerTitleService playerTitleService;

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
                                    new PlayerDashboardResponse.PlayerActivity(0, null), List.of(), List.of()),
                            new PlayerDashboardResponse.PlayerAggregates(
                                    false,
                                    "",
                                    0,
                                    0,
                                    3,
                                    null,
                                    List.of(),
                                    new PlayerDashboardResponse.TechStats(Map.of()),
                                    new PlayerDashboardResponse.FactionWinStats(Map.of()),
                                    new PlayerDashboardResponse.EconomyProfile(0, 0, 0),
                                    new PlayerDashboardResponse.FactionTechSynergy(Map.of()),
                                    new PlayerDashboardResponse.SpeakerImpact(
                                            new PlayerDashboardResponse.SpeakerBucket(0, 0, 0),
                                            new PlayerDashboardResponse.SpeakerBucket(0, 0, 0),
                                            0))),
                    new PlayerDashboardResponse.DashboardSummary(0, 0, 0, 0, 0, null),
                    List.of());
        }

        List<ManagedGame> playerGames = managedPlayer.getGames().stream()
                .filter(game -> isRealParticipant(game, userId))
                .sorted(Comparator.comparingLong(ManagedGame::getCreationDateTime)
                        .reversed())
                .toList();

        String userName = managedPlayer.getName();
        DashboardGameScan scan = scanGames(userId, playerGames);

        PlayerDashboardResponse.TitleSummary titleSummary = getTitleSummary(userId);
        PlayerDashboardResponse.DiceLuckSummary diceLuckSummary = scan.diceLuckSummary();

        int gamesPlayed = playerGames.size();
        int activeGames =
                (int) playerGames.stream().filter(ManagedGame::isActive).count();
        int finishedGames =
                (int) playerGames.stream().filter(ManagedGame::isHasEnded).count();
        int abandonedGames = (int) playerGames.stream()
                .filter(game -> game.isHasEnded() && !game.isHasWinner())
                .count();
        int wins = scan.wins();

        int finishedWithWinner = (int) playerGames.stream()
                .filter(game -> game.isHasEnded() && game.isHasWinner())
                .count();
        Double winPercent = finishedWithWinner == 0 ? null : (wins * 100.0) / finishedWithWinner;

        List<PlayerDashboardResponse.DashboardGame> games = List.copyOf(scan.games());

        PlayerDashboardResponse.DashboardSummary dashboardSummary = new PlayerDashboardResponse.DashboardSummary(
                gamesPlayed, activeGames, finishedGames, abandonedGames, wins, winPercent);
        PlayerDashboardResponse.PlayerInsights insights =
                DashboardBadgeEngine.analyze(userId, scan.totalTurns(), scan.totalTurnTimeMs(), games, titleSummary);
        PlayerDashboardResponse.PlayerAggregates aggregates =
                playerAggregatesService.getOrQueueRefresh(userId, scan.trackedCompletedGameNames());

        return new PlayerDashboardResponse(
                new PlayerDashboardResponse.PlayerProfile(
                        userId,
                        userName,
                        null,
                        scan.latestTiglRank(),
                        titleSummary,
                        diceLuckSummary,
                        insights,
                        aggregates),
                dashboardSummary,
                games);
    }

    private static boolean isRealParticipant(ManagedGame managedGame, String userId) {
        return managedGame.getRealPlayers().stream().anyMatch(player -> userId.equals(player.getId()));
    }

    static DashboardGameScan scanGames(String userId, List<ManagedGame> playerGames) {
        DashboardGameScan scan = new DashboardGameScan(userId);
        for (ManagedGame managedGame : playerGames) {
            Game game = managedGame.getGame();
            if (game != null) {
                scan.add(managedGame, game);
            }
        }
        return scan;
    }

    private static String getTiglRankAtGameStart(Game game, String userId) {
        Player player = game.getPlayer(userId);
        if (player == null || player.getPlayerTIGLRankAtGameStart() == null) {
            return null;
        }
        return player.getPlayerTIGLRankAtGameStart().getName();
    }

    private static boolean isWinner(Game game, String userId) {
        return game.isHasEnded()
                && game.hasWinner()
                && game.getWinners().stream().anyMatch(winner -> userId.equals(winner.getUserID()));
    }

    static final class DashboardGameScan {
        private final String userId;
        private final List<PlayerDashboardResponse.DashboardGame> games = new ArrayList<>();
        private final List<String> trackedCompletedGameNames = new ArrayList<>();
        private String latestTiglRank;
        private int wins;
        private double expectedHits;
        private double actualHits;
        private int totalTurns;
        private long totalTurnTimeMs;

        private DashboardGameScan(String userId) {
            this.userId = userId;
        }

        private void add(ManagedGame managedGame, Game game) {
            if (latestTiglRank == null) {
                latestTiglRank = getTiglRankAtGameStart(game, userId);
            }
            if (isWinner(game, userId)) {
                wins++;
            }
            for (Player player : game.getRealPlayers()) {
                if (!userId.equals(player.getStatsTrackedUserID())) {
                    continue;
                }
                expectedHits += player.getExpectedHitsTimes10() / 10.0;
                actualHits += player.getActualHits();
                totalTurns += Math.max(player.getNumberOfTurns(), 0);
                totalTurnTimeMs += Math.max(player.getTotalTurnTime(), 0);
            }
            PlayerDashboardResponse.DashboardGame dashboardGame = toDashboardGame(managedGame, game, userId);
            if (dashboardGame != null) {
                games.add(dashboardGame);
            }
            if (PlayerAggregatesService.isTrackedCompletedGame(managedGame, game)) {
                trackedCompletedGameNames.add(managedGame.getName());
            }
        }

        private PlayerDashboardResponse.DiceLuckSummary diceLuckSummary() {
            Double ratio = expectedHits > 0 ? actualHits / expectedHits : null;
            return new PlayerDashboardResponse.DiceLuckSummary(actualHits, expectedHits, ratio);
        }

        List<PlayerDashboardResponse.DashboardGame> games() {
            return games;
        }

        int wins() {
            return wins;
        }

        int totalTurns() {
            return totalTurns;
        }

        long totalTurnTimeMs() {
            return totalTurnTimeMs;
        }

        List<String> trackedCompletedGameNames() {
            return trackedCompletedGameNames;
        }

        String latestTiglRank() {
            return latestTiglRank;
        }
    }

    PlayerDashboardResponse.TitleSummary getTitleSummary(String userId) {
        record TitleBuilder(int count, Set<String> gameIds) {}
        Map<String, TitleBuilder> titleToData = new HashMap<>();

        playerTitleService.getEndedGameTitles(userId).stream()
                .filter(earned -> !earned.title().isBlank() && !"**".equals(earned.title()))
                .sorted(Comparator.comparing(
                                (EarnedTitle earned) -> ManagedGameService.getGameNameForSorting(earned.source()))
                        .reversed())
                .forEach(earned -> titleToData.merge(
                        earned.title(),
                        new TitleBuilder(1, new LinkedHashSet<>(List.of(earned.source()))),
                        (current, added) -> {
                            LinkedHashSet<String> gameIds = new LinkedHashSet<>(current.gameIds());
                            gameIds.addAll(added.gameIds());
                            return new TitleBuilder(current.count() + 1, gameIds);
                        }));

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

    private static PlayerDashboardResponse.DashboardGame toDashboardGame(
            ManagedGame managedGame, Game game, String userId) {
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
