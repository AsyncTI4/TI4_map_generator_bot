package ti4.service.statistics;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.map.manage.ManagedPlayer;
import ti4.message.MessageHelper;

@UtilityClass
public class DiceLuckService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(
            new StatisticsPipeline.StatisticsEvent("getDiceLuck", event, () -> getDiceLuck(event)));
    }

    private void getDiceLuck(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        var comparator = getDiceLuckComparator(sortOrderAscending);
        AtomicInteger index = new AtomicInteger(1);
        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        int minimumExpectedHits = event.getOption(Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, 10, OptionMapping::getAsInt);

        StringBuilder sb = new StringBuilder();
        sb.append("## __**Dice Luck**__\n");

        getAllPlayersDiceLuck(ignoreEndedGames).entrySet().stream()
            .filter(entry -> entry.getValue().getKey() > minimumExpectedHits && entry.getValue().getValue() > 0)
            .sorted(comparator)
            .limit(topLimit)
            .forEach(entry -> {
                var user = GameManager.getManagedPlayer(entry.getKey());
                double expectedHits = entry.getValue().getKey();
                int actualHits = entry.getValue().getValue();
                if (expectedHits > 0 && actualHits > 0) {
                    appendDiceLuck(sb, index, user.getName(), expectedHits, actualHits, true);
                }
            });

        MessageHelper.sendMessageToThread(event.getChannel(), "Dice Luck Record", sb.toString());
    }

    String getDiceLuck(List<User> users) {
        List<ManagedGame> userGames = users.stream()
            .map(user -> GameManager.getManagedPlayer(user.getId()))
            .filter(Objects::nonNull)
            .map(ManagedPlayer::getGames)
            .flatMap(Collection::stream)
            .distinct()
            .toList();

        Map<String, Map.Entry<Double, Integer>> playerDiceLucks = new HashMap<>();
        for (ManagedGame game : userGames) {
            getDiceLuckForGame(game.getGame(), playerDiceLucks, new HashMap<>());
        }
        Map<String, Map.Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        for (ManagedGame game : userGames) {
            AverageTurnTimeService.getAverageTurnTimeForGame(game.getGame(), playerTurnTimes, new HashMap<>());
        }

        StringBuilder sb = new StringBuilder();
        AtomicInteger index = new AtomicInteger(1);
        sb.append("## __**Dice Luck**__\n");
        for (User user : users) {
            Map.Entry<Double, Integer> expectedHitsToActualHits = playerDiceLucks.get(user.getId());
            if (expectedHitsToActualHits == null) continue;
            double expectedHits = expectedHitsToActualHits.getKey();
            int actualHits = expectedHitsToActualHits.getValue();
            int turnCount = playerTurnTimes.get(user.getId()).getKey();
            if (expectedHits != 0 && actualHits != 0) {
                appendDiceLuck(sb, index, user.getEffectiveName(), expectedHits, actualHits, false);
                appendDicePerTurn(sb, index, user.getEffectiveName(), expectedHits, turnCount);
            }
        }
        return sb.toString();
    }

    private Map<String, Map.Entry<Double, Integer>> getAllPlayersDiceLuck(boolean ignoreEndedGames) {
        Map<String, Map.Entry<Double, Integer>> playerDiceLucks = new HashMap<>();
        Map<String, Set<Double>> playerAverageDiceLucks = new HashMap<>();

        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        GamesPage.consumeAllGames(
            endedGamesFilter,
            game -> getDiceLuckForGame(game, playerDiceLucks, playerAverageDiceLucks));

        return playerDiceLucks;
    }

    private void getDiceLuckForGame(
        Game game, Map<String, Map.Entry<Double, Integer>> playerDiceLucks,
        Map<String, Set<Double>> playerAverageDiceLucks
    ) {
        for (Player player : game.getRealPlayers()) {
            Map.Entry<Double, Integer> playerDiceLuck = Map.entry(player.getExpectedHitsTimes10() / 10.0, player.getActualHits());
            playerDiceLucks.merge(player.getUserID(), playerDiceLuck,
                (oldEntry, newEntry) -> Map.entry(oldEntry.getKey() + playerDiceLuck.getKey(), oldEntry.getValue() + playerDiceLuck.getValue()));

            if (playerDiceLuck.getKey() == 0) continue;
            Double averageDiceLuck = playerDiceLuck.getValue() / playerDiceLuck.getKey();
            playerAverageDiceLucks.compute(player.getUserID(), (key, value) -> {
                if (value == null) value = new HashSet<>();
                value.add(averageDiceLuck);
                return value;
            });
        }
    }

    private void appendDiceLuck(StringBuilder sb, AtomicInteger index, String playerName, double expectedHits, int actualHits, boolean newLine) {
        double averageDiceLuck = actualHits / expectedHits;
        sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
        sb.append(String.format("%.2f", averageDiceLuck));
        sb.append("` ").append(playerName);
        sb.append("   [").append(actualHits).append("/").append(String.format("%.1f", expectedHits)).append(" actual/expected]");
        if (newLine) {
            sb.append("\n");
        }
        index.getAndIncrement();
    }

    private void appendDicePerTurn(StringBuilder sb, AtomicInteger index, String playerName, double expectedHits, int turns) {
        double averageDiceLuck = expectedHits / turns;
        sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
        sb.append(String.format("%.2f", averageDiceLuck));
        sb.append("` ").append(playerName);
        sb.append("   [").append(String.format("%.1f", expectedHits)).append("/").append(turns).append(" expected hits/turns]");
        sb.append("\n");
        index.getAndIncrement();
    }

    private Comparator<Map.Entry<String, Map.Entry<Double, Integer>>> getDiceLuckComparator(boolean ascending) {
        return (o1, o2) -> {
            double expected1 = o1.getValue().getKey();
            double expected2 = o2.getValue().getKey();
            int actual1 = o1.getValue().getValue();
            int actual2 = o2.getValue().getValue();

            if (expected1 == 0 || expected2 == 0) return -1;

            double luck1 = actual1 / expected1;
            double luck2 = actual2 / expected2;

            return ascending ? Double.compare(luck1, luck2) : -Double.compare(luck1, luck2);
        };
    }
}
