package ti4.commands2.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DiceLuck extends Subcommand {

    public DiceLuck() {
        super(Constants.DICE_LUCK, "Dice luck as recorded by the bot");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, "Minimum number of expected hits to show (Default = 10)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ascending", "True to sort the values in ascending order, lowest to highest (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String diceLuckRecord = getDiceLuck(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Dice Luck Record", diceLuckRecord);
    }

    private String getDiceLuck(SlashCommandInteractionEvent event) {
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
            .forEach(entry  -> {
                var user = AsyncTI4DiscordBot.jda.getUserById(entry.getKey());
                double expectedHits = entry.getValue().getKey();
                int actualHits = entry.getValue().getValue();
                if (expectedHits > 0 && actualHits > 0) {
                    appendDiceLuck(sb, index, user.getName(), expectedHits, actualHits);
                }
            });

        return sb.toString();
    }

    public Map<String, Entry<Double, Integer>> getAllPlayersDiceLuck(boolean ignoreEndedGames) {
        Map<String, Entry<Double, Integer>> playerDiceLucks = new HashMap<>();
        Map<String, Set<Double>> playerAverageDiceLucks = new HashMap<>();

        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        for (Game game : GameManager.getGameNameToGame().values().stream().filter(endedGamesFilter).toList()) {
            for (Player player : game.getRealPlayers()) {
                Entry<Double, Integer> playerDiceLuck = Map.entry(player.getExpectedHitsTimes10() / 10.0, player.getActualHits());
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

        return playerDiceLucks;
    }

    public String getSelectUsersDiceLuck(List<User> users, Map<String, Entry<Double, Integer>> playerDiceLucks) {
        StringBuilder sb = new StringBuilder();
        AtomicInteger index = new AtomicInteger(1);
        sb.append("## __**Dice Luck**__\n");
        for (User user : users) {
            Entry<Double, Integer> userTurnCountTotalTime = playerDiceLucks.get(user.getId());
            double expectedHits = userTurnCountTotalTime.getKey();
            int actualHits = userTurnCountTotalTime.getValue();
            if (expectedHits != 0 && actualHits != 0) {
                appendDiceLuck(sb, index, user.getEffectiveName(), expectedHits, actualHits);
            }
        }
        return sb.toString();
    }

    private void appendDiceLuck(StringBuilder sb, AtomicInteger index, String playerName, double expectedHits, int actualHits) {
        double averageDiceLuck = actualHits / expectedHits;
        sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
        sb.append(String.format("%.2f", averageDiceLuck));
        sb.append("` ").append(playerName);
        sb.append("   [").append(actualHits).append("/").append(String.format("%.1f", expectedHits)).append(" actual/expected]");
        sb.append("\n");
        index.getAndIncrement();
    }

    private Comparator<Entry<String, Entry<Double, Integer>>> getDiceLuckComparator(boolean ascending) {
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
