package ti4.discord.interactions.commands.statistics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
class TwilightsFallSpliceWinRateStatisticsService {

    private static final Set<String> BASIC_ABILITY_IDS = Set.of("wavelength", "antimatter");

    void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showWinRates(event));
    }

    private static void showWinRates(SlashCommandInteractionEvent event) {
        AtomicInteger gameCount = new AtomicInteger();
        TwilightsFallSpliceWinRateStats stats = new TwilightsFallSpliceWinRateStats();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event).and(Game::isTwilightsFallMode),
                game -> {
                    gameCount.incrementAndGet();
                    accumulateGame(game, stats);
                },
                ExecutionLockType.READ);

        String report = buildReport(gameCount.get(), stats);
        MessageHelper.sendMessageToThread(event.getChannel(), "Twilight's Fall splice win rates", report);
    }

    static String buildReport(List<Game> games) {
        TwilightsFallSpliceWinRateStats stats = new TwilightsFallSpliceWinRateStats();
        games.forEach(game -> accumulateGame(game, stats));
        return buildReport(games.size(), stats);
    }

    private static String buildReport(int gameCount, TwilightsFallSpliceWinRateStats stats) {
        if (gameCount == 0) {
            return "No Twilight's Fall games matched the selected filters.";
        }

        StringBuilder sb = new StringBuilder("## __**Twilight's Fall Splice Win Rates**__\n");
        sb.append("Games analyzed: ").append(gameCount).append('\n');

        appendSection(
                sb,
                "Basic Abilities (when picked)",
                stats.basicAbilities,
                TwilightsFallSpliceWinRateStatisticsService::getAbilityName);
        appendSection(
                sb,
                "Abilities (when picked)",
                stats.abilities,
                TwilightsFallSpliceWinRateStatisticsService::getAbilityName);
        appendSection(
                sb,
                "Unit Upgrades (when picked)",
                stats.unitUpgrades,
                TwilightsFallSpliceWinRateStatisticsService::getUnitName);
        appendSection(
                sb, "Genomes (when picked)", stats.genomes, TwilightsFallSpliceWinRateStatisticsService::getGenomeName);
        appendSectionEstimated(
                sb,
                "Abilities (when available, estimated)",
                stats.abilities,
                TwilightsFallSpliceWinRateStatisticsService::getAbilityName);
        appendSectionEstimated(
                sb,
                "Unit Upgrades (when available, estimated)",
                stats.unitUpgrades,
                TwilightsFallSpliceWinRateStatisticsService::getUnitName);
        appendSectionEstimated(
                sb,
                "Genomes (when available, estimated)",
                stats.genomes,
                TwilightsFallSpliceWinRateStatisticsService::getGenomeName);
        return sb.toString();
    }

    private static void appendSection(
            StringBuilder sb, String title, Map<String, WinRateCount> stats, Function<String, String> displayName) {
        sb.append("\n**").append(title).append("**\n");
        stats.entrySet().stream().sorted(getWinRateComparator(displayName)).forEach(entry -> sb.append("- ")
                .append(displayName.apply(entry.getKey()))
                .append(": ")
                .append(entry.getValue())
                .append('\n'));
    }

    private static void appendSectionEstimated(
            StringBuilder sb, String title, Map<String, WinRateCount> stats, Function<String, String> displayName) {
        int maxTotal = stats.values().stream().mapToInt(c -> c.total).max().orElse(0);
        sb.append("\n**").append(title).append("**\n");
        if (maxTotal == 0) {
            return;
        }
        stats.entrySet().stream()
                .sorted(Comparator.<Entry<String, WinRateCount>, Integer>comparing(
                                entry -> entry.getValue().wins, Comparator.reverseOrder())
                        .thenComparing(entry -> displayName.apply(entry.getKey())))
                .forEach(entry -> sb.append("- ")
                        .append(displayName.apply(entry.getKey()))
                        .append(": ")
                        .append(entry.getValue().toEstimatedString(maxTotal))
                        .append('\n'));
    }

    private static Comparator<Entry<String, WinRateCount>> getWinRateComparator(Function<String, String> displayName) {
        return Comparator.<Entry<String, WinRateCount>, Boolean>comparing(entry -> entry.getValue().total == 0)
                .thenComparing(
                        (Entry<String, WinRateCount> entry) -> entry.getValue().getWinRate(), Comparator.reverseOrder())
                .thenComparing((Entry<String, WinRateCount> entry) -> entry.getValue().total, Comparator.reverseOrder())
                .thenComparing(entry -> displayName.apply(entry.getKey()));
    }

    private static void accumulateGame(Game game, TwilightsFallSpliceWinRateStats stats) {
        List<Player> winners = game.getWinners();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            boolean isWinner = winners.contains(player);
            for (String tech : player.getTechs()) {
                accumulateSingleItem(tech, stats.basicAbilities, isWinner);
                accumulateSingleItem(tech, stats.abilities, isWinner);
            }
            accumulateItems(player.getUnitsOwned(), stats.unitUpgrades, isWinner);
            accumulateItems(player.getLeaderIDs(), stats.genomes, isWinner);
        }
    }

    private static void accumulateSingleItem(String item, Map<String, WinRateCount> stats, boolean isWinner) {
        WinRateCount count = stats.get(item);
        if (count == null) {
            return;
        }
        count.total++;
        if (isWinner) {
            count.wins++;
        }
    }

    private static void accumulateItems(Iterable<String> items, Map<String, WinRateCount> stats, boolean isWinner) {
        for (String item : items) {
            WinRateCount count = stats.get(item);
            if (count == null) {
                continue;
            }
            count.total++;
            if (isWinner) {
                count.wins++;
            }
        }
    }

    private static String getAbilityName(String techId) {
        TechnologyModel tech = Mapper.getTech(techId);
        return tech == null ? techId : tech.getName();
    }

    private static String getUnitName(String unitId) {
        UnitModel unit = Mapper.getUnit(unitId);
        return unit == null ? unitId : unit.getName();
    }

    private static String getGenomeName(String leaderId) {
        LeaderModel leader = Mapper.getLeader(leaderId);
        return leader == null ? leaderId : leader.getTFNameIfAble();
    }

    private static class TwilightsFallSpliceWinRateStats {
        private final Map<String, WinRateCount> basicAbilities = initializeBasicAbilities();
        private final Map<String, WinRateCount> abilities = initializeAbilities();
        private final Map<String, WinRateCount> unitUpgrades = initializeUnitUpgrades();
        private final Map<String, WinRateCount> genomes = initializeGenomes();
    }

    private static Map<String, WinRateCount> initializeBasicAbilities() {
        return Mapper.getTechs().values().stream()
                .filter(tech -> tech.getSource() == ComponentSource.twilights_fall)
                .filter(tech -> BASIC_ABILITY_IDS.contains(tech.getAlias()))
                .collect(LinkedHashMap::new, (map, tech) -> map.put(tech.getAlias(), new WinRateCount()), Map::putAll);
    }

    private static Map<String, WinRateCount> initializeAbilities() {
        return Mapper.getTechs().values().stream()
                .filter(tech -> tech.getSource() == ComponentSource.twilights_fall)
                .filter(tech -> !BASIC_ABILITY_IDS.contains(tech.getAlias()))
                .collect(LinkedHashMap::new, (map, tech) -> map.put(tech.getAlias(), new WinRateCount()), Map::putAll);
    }

    private static Map<String, WinRateCount> initializeUnitUpgrades() {
        return Mapper.getUnits().values().stream()
                .filter(unit -> unit.getSource() == ComponentSource.twilights_fall)
                .filter(unit -> unit.getIsUpgrade())
                .collect(LinkedHashMap::new, (map, unit) -> map.put(unit.getId(), new WinRateCount()), Map::putAll);
    }

    private static Map<String, WinRateCount> initializeGenomes() {
        return Mapper.getDeck(Constants.TF_GENOME).getNewDeck().stream()
                .collect(LinkedHashMap::new, (map, leaderId) -> map.put(leaderId, new WinRateCount()), Map::putAll);
    }

    private static class WinRateCount {
        private int wins;
        private int total;

        private double getWinRate() {
            return total == 0 ? -1 : wins * 1.0 / total;
        }

        @Override
        public String toString() {
            if (total == 0) {
                return "0/0 (0%)";
            }
            return wins + "/" + total + " (" + Math.round(wins * 100.0 / total) + "%)";
        }

        private String toEstimatedString(int denominator) {
            return wins + "/" + denominator + " (" + Math.round(wins * 100.0 / denominator) + "%)";
        }
    }
}
