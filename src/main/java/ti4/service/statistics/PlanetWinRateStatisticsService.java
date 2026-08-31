package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;

@UtilityClass
public class PlanetWinRateStatisticsService {

    public static final String POK_ONLY_OPTION = "pok_only";
    public static final String HOME_LOSS_FACTION_OPTION = "home_loss_faction";

    private static final int HOME_LOSS_GAMES_LISTED = 40;

    /**
     * Coexistence carries any faction through a home planet loss, but only the Deepwrought
     * Scholarate does it often enough for the count to be worth a line of its own.
     */
    private static final String COEXISTING_FACTION = "deepwrought";

    /** The factions that coexist by design, and so the only ones the coexistence section reports. */
    private static final List<String> COEXISTING_FACTIONS = List.of(COEXISTING_FACTION, "titans");

    private static final int BAND_SIZE = 2;
    private static final int OPEN_ENDED_BAND_START = 11;
    private static final int COEXIST_OPEN_ENDED_BAND_START = 5;
    private static final int MINIMUM_FACTION_PLAYERS = 25;
    private static final int SKIPPED_FACTIONS_LISTED = 10;

    private static final Comparator<Entry<String, WinRateCount>> BY_WIN_RATE_DESC = Comparator.comparingDouble(
                    (Entry<String, WinRateCount> entry) -> entry.getValue().getWinRate())
            .thenComparingInt(entry -> entry.getValue().getPlayers())
            .reversed()
            .thenComparing(entry -> planetName(entry.getKey()));

    private static final Comparator<Entry<String, PlanetHoldingStats>> BY_HOME_LOSS_RATE_DESC =
            Comparator.comparingDouble((Entry<String, PlanetHoldingStats> entry) ->
                            entry.getValue().homeLossRate())
                    .reversed()
                    .thenComparing(Entry::getKey);

    private static final Comparator<Entry<String, PlanetHoldingStats>> BY_AVERAGE_NON_HOME_PLANETS_DESC =
            Comparator.comparingDouble((Entry<String, PlanetHoldingStats> entry) ->
                            entry.getValue().averageNonHomePlanets())
                    .reversed()
                    .thenComparing(Entry::getKey);

    private static final Comparator<Entry<String, PlanetHoldingStats>> BY_AVERAGE_COEXISTED_PLANETS_DESC =
            Comparator.comparingDouble((Entry<String, PlanetHoldingStats> entry) ->
                            entry.getValue().averageCoexistedPlanets())
                    .reversed()
                    .thenComparing(Entry::getKey);

    public static void queueReply(SlashCommandInteractionEvent event) {
        boolean pokOnly = event.getOption(POK_ONLY_OPTION, false, OptionMapping::getAsBoolean);
        String homeLossFaction = event.getOption(HOME_LOSS_FACTION_OPTION, null, OptionMapping::getAsString);
        StatisticsPipeline.queue(event, () -> showPlanetWinRates(event, pokOnly, homeLossFaction));
    }

    private static void showPlanetWinRates(
            SlashCommandInteractionEvent event, boolean pokOnly, String homeLossFaction) {
        PlanetWinRateStats stats = new PlanetWinRateStats(pokOnly, homeLossFaction);
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter()
                        .and(game -> isEligibleGameType(game, pokOnly)),
                game -> accumulateGame(game, stats),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(event.getChannel(), "Planet win rates", buildReport(stats));
    }

    static List<String> buildReport(List<Game> games, boolean pokOnly) {
        return buildReport(games, pokOnly, null);
    }

    static List<String> buildReport(List<Game> games, boolean pokOnly, String homeLossFaction) {
        PlanetWinRateStats stats = new PlanetWinRateStats(pokOnly, homeLossFaction);
        games.forEach(game -> accumulateGame(game, stats));
        return buildReport(stats);
    }

    static boolean isEligibleGameType(Game game, boolean pokOnly) {
        if (game.isTwilightsFallMode()) {
            return false;
        }
        return pokOnly ? game.isProphecyOfKings() && !game.isThundersEdge() : sampledGameType(game);
    }

    private static boolean sampledGameType(Game game) {
        return game.isThundersEdge() || game.isProphecyOfKings();
    }

    private static void accumulateGame(Game game, PlanetWinRateStats stats) {
        Player winner = game.getWinner().orElse(null);
        if (winner == null || !isEligibleGameType(game, stats.pokOnly)) {
            return;
        }
        stats.games++;

        List<PlayerHome> seats = new ArrayList<>();
        Set<String> everyHomePlanetOnTheBoard = new HashSet<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            if (StringUtils.isBlank(player.getFaction())) {
                continue;
            }
            Set<String> homePlanets = getHomePlanets(game, player);
            if (homePlanets.isEmpty()) {
                stats.playersWithoutAKnownHome++;
                stats.skippedPlayersByFaction.merge(player.getFaction(), 1, Integer::sum);
                stats.skippedGameNames.putIfAbsent(player.getFaction(), game.getName());
                continue;
            }
            seats.add(new PlayerHome(player, homePlanets));
            everyHomePlanetOnTheBoard.addAll(homePlanets);
        }

        for (PlayerHome seat : seats) {
            String faction = seat.player().getFaction();
            Set<String> homePlanets = seat.homePlanets();
            Set<String> controlledPlanets = seat.player().getPlanets().stream()
                    .filter(planet -> !isOcean(planet))
                    .collect(Collectors.toCollection(HashSet::new));
            boolean isWinner = faction.equals(winner.getFaction());
            int nonHomePlanets = (int) controlledPlanets.stream()
                    .filter(planet -> !homePlanets.contains(planet))
                    .filter(planet -> !isTradeStation(planet))
                    .count();
            Set<String> coexistedOn = coexistedPlanets(game, seat.player(), controlledPlanets);
            // A home system is kept only by holding or coexisting on every planet in it.
            boolean lostAHomePlanet = homePlanets.stream()
                    .anyMatch(planet -> !controlledPlanets.contains(planet) && !coexistedOn.contains(planet));
            boolean coexistedThroughALoss =
                    !lostAHomePlanet && homePlanets.stream().anyMatch(coexistedOn::contains);

            if (lostAHomePlanet && faction.equalsIgnoreCase(stats.homeLossFaction)) {
                stats.homeLossGames.add(describeHomeLoss(game, seat, controlledPlanets, coexistedOn));
            }

            SeatOutcome outcome = new SeatOutcome(
                    isWinner,
                    nonHomePlanets,
                    coexistedOn.size(),
                    lostAHomePlanet,
                    coexistedThroughALoss,
                    wasSilverFlamed(game, faction));
            stats.overall.record(outcome);
            for (String factionKey : FactionStatisticsHelper.getStatisticsFactionKeys(faction)) {
                stats.byFaction
                        .computeIfAbsent(factionKey, _ -> new PlanetHoldingStats())
                        .record(outcome);
            }

            controlledPlanets.stream()
                    .filter(planet -> !everyHomePlanetOnTheBoard.contains(planet) && !isFactionHomeworld(planet))
                    .forEach(planet -> stats.byPlanet
                            .computeIfAbsent(planet, _ -> new WinRateCount())
                            .record(isWinner));
        }
    }

    /**
     * Planets the player does not control but still has ground forces or structures on - the same
     * reading of coexistence {@link Player#getPlanetsForScoring} takes.
     */
    private static Set<String> coexistedPlanets(Game game, Player player, Set<String> controlledPlanets) {
        Set<String> coexistedOn = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String planetId = planet.getName();
                if (controlledPlanets.contains(planetId) || isOcean(planetId)) {
                    continue;
                }
                if (planet.hasGroundForces(player) || planet.hasStructures(player)) {
                    coexistedOn.add(planetId);
                }
            }
        }
        return coexistedOn;
    }

    /**
     * One line per home planet loss for the faction being debugged: which planets went, who has them
     * now, and whether the home system is still on the board at all.
     */
    private static String describeHomeLoss(
            Game game, PlayerHome seat, Set<String> controlledPlanets, Set<String> coexistedOn) {
        StringBuilder sb = new StringBuilder("- `").append(game.getName()).append("`");
        for (String homePlanet : seat.homePlanets().stream().sorted().toList()) {
            if (controlledPlanets.contains(homePlanet) || coexistedOn.contains(homePlanet)) {
                continue;
            }
            UnitHolder unitHolder = game.getUnitHolderFromPlanet(homePlanet);
            String holder = game.getRealAndEliminatedPlayers().stream()
                    .filter(player -> player.getPlanets().contains(homePlanet))
                    .map(Player::getFaction)
                    .findFirst()
                    .orElse(unitHolder == null ? "not on the board" : "nobody");
            sb.append(' ')
                    .append(planetName(homePlanet))
                    .append(" (")
                    .append(holder)
                    .append(')');
        }
        if (wasSilverFlamed(game, seat.player().getFaction())) {
            sb.append(" [silverFlamed]");
        }
        return sb.append('\n').toString();
    }

    /**
     * The Silver Flame purges a home system outright - SilverFlameService strips its planets from
     * everyone, removes the tile and drops a silver flame tile in its place, so nobody took it. It
     * stores the one faction it happened to under a single key, so this is an equality test rather
     * than a lookup in a list.
     */
    private static boolean wasSilverFlamed(Game game, String faction) {
        return game.getStoredValue("silverFlamed").equalsIgnoreCase(faction);
    }

    private static boolean isOcean(String planetId) {
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        return planetModel != null && planetModel.isFake();
    }

    private static boolean isTradeStation(String planetId) {
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        return planetModel != null && planetModel.isSpaceStation();
    }

    private static boolean isFactionHomeworld(String planetId) {
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        return planetModel != null && StringUtils.isNotBlank(planetModel.getFactionHomeworld());
    }

    private static Set<String> getHomePlanets(Game game, Player player) {
        FactionModel factionModel = player.getFactionModel();
        if (factionModel != null) {
            Set<String> homePlanets = factionModel.getHomePlanets().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(planet -> AliasHandler.resolvePlanet(planet.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toSet());
            if (!homePlanets.isEmpty()) {
                return homePlanets;
            }
        }
        return getHomePlanetsFromTheBoard(game, player);
    }

    private static Set<String> getHomePlanetsFromTheBoard(Game game, Player player) {
        Set<String> atTheirPosition = Stream.of(player.getHomeSystemPosition(), player.getPlayerStatsAnchorPosition())
                .filter(position -> StringUtils.isNotBlank(position) && !"null".equalsIgnoreCase(position))
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && tile.isHomeSystem())
                .map(tile -> tile.getPlanetUnitHolders().stream()
                        .map(UnitHolder::getName)
                        .collect(Collectors.toSet()))
                .filter(homePlanets -> !homePlanets.isEmpty())
                .findFirst()
                .orElse(Set.of());
        return atTheirPosition.isEmpty() ? homeworldsBelongingTo(game, player.getFaction()) : atTheirPosition;
    }

    private static Set<String> homeworldsBelongingTo(Game game, String faction) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .map(UnitHolder::getName)
                .filter(planetId -> isHomeworldOf(planetId, faction))
                .collect(Collectors.toSet());
    }

    /**
     * Older games stored Keleres as a bare "keleres", which no faction file answers to, while its
     * homeworlds are marked for keleresa, keleresm and keleresx. Only one of those is ever on a
     * board, so the alias the planet names starting with the one the player carries picks it out.
     */
    private static boolean isHomeworldOf(String planetId, String faction) {
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        if (planetModel == null || StringUtils.isBlank(planetModel.getFactionHomeworld())) {
            return false;
        }
        String homeworldFaction = planetModel.getFactionHomeworld().toLowerCase(Locale.ROOT);
        return homeworldFaction.startsWith(faction.toLowerCase(Locale.ROOT));
    }

    private record PlayerHome(Player player, Set<String> homePlanets) {}

    private record SeatOutcome(
            boolean isWinner,
            int nonHomePlanets,
            int coexistedPlanets,
            boolean lostHome,
            boolean coexistedThroughALoss,
            boolean silverFlamed) {}

    private static List<String> buildReport(PlanetWinRateStats stats) {
        List<String> blocks = new ArrayList<>();

        StringBuilder header = new StringBuilder("## __**Planet Win Rates**__\n");
        header.append("_Planets each player controlled at the end of the game. Oceans are never counted._\n");
        header.append(
                        stats.pokOnly
                                ? "_Prophecy of Kings games, no Thunder's Edge."
                                : "_Thunder's Edge and Prophecy" + " of Kings games.")
                .append(" 6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario,"
                        + " non-Twilight's-Fall, with winners._\n");
        if (stats.overall.players == 0) {
            header.append('\n')
                    .append(
                            stats.games == 0
                                    ? "No games matched."
                                    : "None of the " + stats.games
                                            + " matching games had a player whose home planets could be identified.")
                    .append('\n');
            blocks.add(header.toString());
            return blocks;
        }
        header.append("Games analyzed: ")
                .append(stats.games)
                .append(" | Players analyzed: ")
                .append(stats.overall.players)
                .append('\n');
        blocks.add(header.toString());
        appendSkippedPlayersSection(blocks, stats);

        appendNonHomePlanetsSection(blocks, stats);
        if (!stats.pokOnly) {
            appendCoexistedPlanetsSection(blocks, stats);
        }
        appendHomePlanetsLostSection(blocks, stats);
        appendPerPlanetSection(blocks, stats);
        appendHomeLossDebugSection(blocks, stats);

        return blocks;
    }

    private static void appendHomeLossDebugSection(List<String> blocks, PlanetWinRateStats stats) {
        if (StringUtils.isBlank(stats.homeLossFaction)) {
            return;
        }
        blocks.add("\n### Home planet losses for `" + stats.homeLossFaction + "`\n"
                + "_Each line names the game, the home planets they ended without, and who holds each one._\n");
        if (stats.homeLossGames.isEmpty()) {
            blocks.add("- They never lost a home planet.\n");
            return;
        }
        stats.homeLossGames.stream().limit(HOME_LOSS_GAMES_LISTED).forEach(blocks::add);
        if (stats.homeLossGames.size() > HOME_LOSS_GAMES_LISTED) {
            blocks.add("- and " + (stats.homeLossGames.size() - HOME_LOSS_GAMES_LISTED) + " more\n");
        }
    }

    private static void appendSkippedPlayersSection(List<String> blocks, PlanetWinRateStats stats) {
        if (stats.playersWithoutAKnownHome == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder("\n### Skipped players\n");
        sb.append("_")
                .append(stats.playersWithoutAKnownHome)
                .append(" player(s) had no home planets on file for their faction and no home system on the board, so"
                        + " they are in none of the numbers above or below. Each row names a game to look at._\n");

        List<Entry<String, Integer>> byFaction = stats.skippedPlayersByFaction.entrySet().stream()
                .sorted(Entry.<String, Integer>comparingByValue().reversed().thenComparing(Entry::getKey))
                .toList();
        byFaction.stream().limit(SKIPPED_FACTIONS_LISTED).forEach(entry -> sb.append("- `")
                .append(entry.getKey())
                .append("` - ")
                .append(entry.getValue())
                .append(" player(s), e.g. game `")
                .append(stats.skippedGameNames.get(entry.getKey()))
                .append("`\n"));
        if (byFaction.size() > SKIPPED_FACTIONS_LISTED) {
            sb.append("- and ")
                    .append(byFaction.size() - SKIPPED_FACTIONS_LISTED)
                    .append(" more faction(s)\n");
        }
        blocks.add(sb.toString());
    }

    private static void appendNonHomePlanetsSection(List<String> blocks, PlanetWinRateStats stats) {
        blocks.add("\n### Win rate by non-home planets controlled\n"
                + "_Planets held at the end of the game outside the player's own home system."
                + " Trade stations and coexisting do not count._\n"
                + "_Each row reads: win rate (wins/players; share of that group's players who got that far)._\n");

        blocks.add(renderBandedGroup("**All factions**", stats.overall));
        wellSampledFactions(stats)
                .sorted(BY_AVERAGE_NON_HOME_PLANETS_DESC)
                .forEach(entry -> blocks.add(renderBandedGroup(factionLabel(entry.getKey()), entry.getValue())));
    }

    private static String renderBandedGroup(String label, PlanetHoldingStats group) {
        StringBuilder sb = new StringBuilder("- ");
        sb.append(label)
                .append(": ")
                .append(String.format("%.2f", group.averageNonHomePlanets()))
                .append(" non-home planets on average, ")
                .append(ActionCardStatsService.formatPercent(group.winRate()))
                .append(" win rate from ");
        ActionCardStatsService.appendCount(sb, group.players, "player");
        sb.append('\n');

        group.playersByBand.forEach((bandStart, count) -> {
            sb.append("  - ");
            appendBandLabel(sb, bandStart);
            sb.append(": ")
                    .append(ActionCardStatsService.formatPercent(count.getWinRate()))
                    .append(" (")
                    .append(count.getWins())
                    .append('/')
                    .append(count.getPlayers())
                    .append("; ")
                    .append(ActionCardStatsService.formatPercent(count.getPlayers() / (double) group.players))
                    .append(")\n");
        });
        return sb.toString();
    }

    private static int bandStartFor(int planets) {
        if (planets == 0) {
            return 0;
        }
        if (planets >= OPEN_ENDED_BAND_START) {
            return OPEN_ENDED_BAND_START;
        }
        return (planets - 1) / BAND_SIZE * BAND_SIZE + 1;
    }

    private static void appendBandLabel(StringBuilder sb, int bandStart) {
        if (bandStart == 0) {
            sb.append("0 planets");
            return;
        }
        if (bandStart >= OPEN_ENDED_BAND_START) {
            sb.append(bandStart).append("+ planets");
            return;
        }
        sb.append(bandStart).append('-').append(bandStart + BAND_SIZE - 1).append(" planets");
    }

    private static int coexistBandStartFor(int planets) {
        return Math.min(planets, COEXIST_OPEN_ENDED_BAND_START);
    }

    private static void appendCoexistBandLabel(StringBuilder sb, int bandStart) {
        sb.append(bandStart)
                .append(
                        bandStart >= COEXIST_OPEN_ENDED_BAND_START
                                ? "+ planets"
                                : bandStart == 1 ? " planet" : " planets");
    }

    private static void appendCoexistedPlanetsSection(List<String> blocks, PlanetWinRateStats stats) {
        blocks.add("\n### Win rate by planets coexisted on\n"
                + "_Each row reads: win rate (wins/players; share of that group's players who got that far)._\n");

        List<Entry<String, PlanetHoldingStats>> reported = COEXISTING_FACTIONS.stream()
                .filter(stats.byFaction::containsKey)
                .map(faction -> Map.entry(faction, stats.byFaction.get(faction)))
                .sorted(BY_AVERAGE_COEXISTED_PLANETS_DESC)
                .toList();
        if (reported.isEmpty()) {
            blocks.add("- Neither faction appeared in the sample.\n");
            return;
        }
        reported.forEach(entry -> blocks.add(renderCoexistGroup(factionLabel(entry.getKey()), entry.getValue())));
    }

    private static String renderCoexistGroup(String label, PlanetHoldingStats group) {
        StringBuilder sb = new StringBuilder("- ");
        sb.append(label)
                .append(": ")
                .append(String.format("%.2f", group.averageCoexistedPlanets()))
                .append(" planets coexisted on on average, ")
                .append(ActionCardStatsService.formatPercent(group.winRate()))
                .append(" win rate from ");
        ActionCardStatsService.appendCount(sb, group.players, "player");
        sb.append('\n');

        group.playersByCoexistBand.forEach((bandStart, count) -> {
            sb.append("  - ");
            appendCoexistBandLabel(sb, bandStart);
            sb.append(": ")
                    .append(ActionCardStatsService.formatPercent(count.getWinRate()))
                    .append(" (")
                    .append(count.getWins())
                    .append('/')
                    .append(count.getPlayers())
                    .append("; ")
                    .append(ActionCardStatsService.formatPercent(count.getPlayers() / (double) group.players))
                    .append(")\n");
        });
        return sb.toString();
    }

    private static void appendHomePlanetsLostSection(List<String> blocks, PlanetWinRateStats stats) {
        blocks.add("\n### Home planets lost\n"
                + "_Players who ended the game without every planet of their own home system. Coexisting on one"
                + " counts as holding it._\n");

        blocks.add(renderCombinedHomePlanetsLostLine(stats.overall));
        wellSampledFactions(stats)
                .sorted(BY_HOME_LOSS_RATE_DESC)
                .forEach(entry -> blocks.add(renderFactionHomePlanetsLostLine(
                        factionLabel(entry.getKey()),
                        entry.getValue(),
                        COEXISTING_FACTION.equalsIgnoreCase(entry.getKey()))));
    }

    private static String renderCombinedHomePlanetsLostLine(PlanetHoldingStats group) {
        StringBuilder sb = appendHomePlanetsLostCount(new StringBuilder("- **All factions**: "), group);
        sb.append(" of players lost a home planet");
        if (group.lostAHomePlanet.getPlayers() > 0) {
            sb.append(". ")
                    .append(ActionCardStatsService.formatPercent(group.lostAHomePlanet.getWinRate()))
                    .append(" win rate when they did, ")
                    .append(ActionCardStatsService.formatPercent(group.heldEveryHomePlanet.getWinRate()))
                    .append(" when they did not");
        }
        // No coexistence line here: it belongs to the one faction that can do it, not to a total
        // that every other faction is counted into.
        appendSilverFlames(sb, group);
        return sb.append('\n').toString();
    }

    private static String renderFactionHomePlanetsLostLine(
            String label, PlanetHoldingStats group, boolean showCoexistence) {
        StringBuilder sb =
                appendHomePlanetsLostCount(new StringBuilder("- ").append(label).append(": "), group);
        sb.append(" homes lost");
        if (group.lostAHomePlanet.getPlayers() > 0) {
            sb.append(". ")
                    .append(ActionCardStatsService.formatPercent(group.lostAHomePlanet.getWinRate()))
                    .append(" win rate, ")
                    .append(ActionCardStatsService.formatPercent(group.heldEveryHomePlanet.getWinRate()))
                    .append(" otherwise");
        }
        if (showCoexistence) {
            appendCoexistedThrough(sb, group);
        }
        appendSilverFlames(sb, group);
        return sb.append('\n').toString();
    }

    private static void appendSilverFlames(StringBuilder sb, PlanetHoldingStats group) {
        if (group.silverFlamed.getPlayers() == 0) {
            return;
        }
        sb.append(". ").append(group.silverFlamed.getPlayers()).append(" Silver Flames.");
    }

    private static void appendCoexistedThrough(StringBuilder sb, PlanetHoldingStats group) {
        if (group.coexistedThroughLosses == 0) {
            return;
        }
        sb.append(". Coexisted through ")
                .append(group.coexistedThroughLosses)
                .append('/')
                .append(group.homeControlLosses())
                .append(" (")
                .append(ActionCardStatsService.formatPercent(group.coexistedThroughRate()))
                .append(") of home system losses.");
    }

    private static StringBuilder appendHomePlanetsLostCount(StringBuilder sb, PlanetHoldingStats group) {
        return sb.append(group.lostAHomePlanet.getPlayers())
                .append('/')
                .append(group.players)
                .append(" (")
                .append(ActionCardStatsService.formatPercent(group.homeLossRate()))
                .append(')');
    }

    private static void appendPerPlanetSection(List<String> blocks, PlanetWinRateStats stats) {
        blocks.add("\n### Win rate by planet controlled\n"
                + "_A player's win rate when they held the planet at the end of the game. Home planets are left"
                + " out._\n");

        List<Entry<String, WinRateCount>> ranked =
                stats.byPlanet.entrySet().stream().sorted(BY_WIN_RATE_DESC).toList();
        if (ranked.isEmpty()) {
            blocks.add("- No planets were held.\n");
            return;
        }
        ranked.forEach(entry -> blocks.add(renderPlanetLine(entry)));
    }

    private static String renderPlanetLine(Entry<String, WinRateCount> entry) {
        WinRateCount count = entry.getValue();
        return "* `" + StringUtils.leftPad(Long.toString(count.percent()), 3) + "%` (" + count.getWins() + '/'
                + count.getPlayers() + ") " + planetName(entry.getKey()) + '\n';
    }

    private static Stream<Entry<String, PlanetHoldingStats>> wellSampledFactions(PlanetWinRateStats stats) {
        return stats.byFaction.entrySet().stream().filter(entry -> entry.getValue().players >= MINIMUM_FACTION_PLAYERS);
    }

    private static String planetName(String planetId) {
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        String name = planetModel == null ? null : planetModel.getNameNullSafe();
        return StringUtils.isBlank(name) ? planetId : name;
    }

    private static String factionLabel(String faction) {
        FactionModel factionModel = Mapper.getFaction(faction);
        String factionName = factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : faction;
        return FactionStatisticsHelper.getFactionEmoji(faction) + " **" + factionName + "**";
    }

    private static class PlanetWinRateStats {
        private PlanetWinRateStats(boolean pokOnly, String homeLossFaction) {
            this.pokOnly = pokOnly;
            this.homeLossFaction = homeLossFaction;
        }

        final boolean pokOnly;

        final String homeLossFaction;

        final List<String> homeLossGames = new ArrayList<>();

        final PlanetHoldingStats overall = new PlanetHoldingStats();

        final Map<String, PlanetHoldingStats> byFaction = new HashMap<>();

        final Map<String, WinRateCount> byPlanet = new HashMap<>();

        final Map<String, Integer> skippedPlayersByFaction = new HashMap<>();

        final Map<String, String> skippedGameNames = new HashMap<>();

        int games;
        int playersWithoutAKnownHome;
    }

    private static class PlanetHoldingStats {
        final NavigableMap<Integer, WinRateCount> playersByBand = new TreeMap<>();

        final WinRateCount lostAHomePlanet = new WinRateCount();

        final WinRateCount heldEveryHomePlanet = new WinRateCount();

        final WinRateCount silverFlamed = new WinRateCount();

        final NavigableMap<Integer, WinRateCount> playersByCoexistBand = new TreeMap<>();

        int players;
        int wins;
        int nonHomePlanetsAtTheirRealCounts;
        int coexistedPlanetsAtTheirRealCounts;
        int coexistedThroughLosses;

        void record(SeatOutcome outcome) {
            players++;
            if (outcome.isWinner()) {
                wins++;
            }
            nonHomePlanetsAtTheirRealCounts += outcome.nonHomePlanets();
            coexistedPlanetsAtTheirRealCounts += outcome.coexistedPlanets();
            if (outcome.coexistedThroughALoss()) {
                coexistedThroughLosses++;
            }
            playersByBand
                    .computeIfAbsent(bandStartFor(outcome.nonHomePlanets()), _ -> new WinRateCount())
                    .record(outcome.isWinner());
            playersByCoexistBand
                    .computeIfAbsent(coexistBandStartFor(outcome.coexistedPlanets()), _ -> new WinRateCount())
                    .record(outcome.isWinner());
            (outcome.lostHome() ? lostAHomePlanet : heldEveryHomePlanet).record(outcome.isWinner());
            if (outcome.silverFlamed()) {
                silverFlamed.record(outcome.isWinner());
            }
        }

        double averageCoexistedPlanets() {
            return players == 0 ? 0 : (double) coexistedPlanetsAtTheirRealCounts / players;
        }

        /** Losses of control, before coexistence hands some of them back. */
        int homeControlLosses() {
            return lostAHomePlanet.getPlayers() + coexistedThroughLosses;
        }

        double coexistedThroughRate() {
            int losses = homeControlLosses();
            return losses == 0 ? 0 : (double) coexistedThroughLosses / losses;
        }

        double winRate() {
            return players == 0 ? 0 : (double) wins / players;
        }

        double averageNonHomePlanets() {
            return players == 0 ? 0 : (double) nonHomePlanetsAtTheirRealCounts / players;
        }

        double homeLossRate() {
            return players == 0 ? 0 : (double) lostAHomePlanet.getPlayers() / players;
        }
    }

    @Getter
    private static class WinRateCount {
        private int players;
        private int wins;

        void record(boolean isWinner) {
            players++;
            if (isWinner) {
                wins++;
            }
        }

        double getWinRate() {
            return players == 0 ? 0 : (double) wins / players;
        }

        long percent() {
            return players == 0 ? 0 : Math.round(wins * 100.0 / players);
        }
    }
}
