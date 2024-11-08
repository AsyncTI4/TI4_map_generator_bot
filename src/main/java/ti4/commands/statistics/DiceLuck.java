package ti4.commands.statistics;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class DiceLuck extends StatisticsSubcommandData {

    public DiceLuck() {
        super(Constants.DICE_LUCK, "Dice luck as recorded by the bot");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, "Minimum number of expected hits to show (Default = 10)").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ascending", "True to sort the values in ascending order, lowest to highest (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getDiceLuck(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Dice Luck Record", text);
    }

    private String getDiceLuck(SlashCommandInteractionEvent event) {

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        Map<String, Entry<Double, Integer>> playerDiceLucks = getAllPlayersDiceLuck(ignoreEndedGames);

        //  HashMap<String, Double> playerMedianDiceLucks = playerAverageDiceLucks.entrySet().stream().map(e -> Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList()))).collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Dice Luck**__\n");

        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        Comparator<Entry<String, Entry<Double, Integer>>> comparator = (o1, o2) -> {
            double o1TurnCount = o1.getValue().getKey();
            double o2TurnCount = o2.getValue().getKey();
            int o1total = o1.getValue().getValue();
            int o2total = o2.getValue().getValue();
            if (o1TurnCount == 0 || o2TurnCount == 0) return -1;

            double total1 = o1total / o1TurnCount;
            double total2 = o2total / o2TurnCount;
            return sortOrderAscending ? Double.compare(total1, total2) : -Double.compare(total1, total2);
        };

        AtomicInteger index = new AtomicInteger(1);
        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        int minimumTurnsToShow = event.getOption(Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, 10, OptionMapping::getAsInt);

        playerDiceLucks.entrySet().stream()
            .filter(o -> o.getValue().getValue() != 0 && o.getValue().getKey() > minimumTurnsToShow)
            .sorted(comparator)
            .limit(topLimit)
            .forEach(userTurnCountTotalTime -> {
                User user = AsyncTI4DiscordBot.jda.getUserById(userTurnCountTotalTime.getKey());
                double expectedHits = userTurnCountTotalTime.getValue().getKey();
                int actualHits = userTurnCountTotalTime.getValue().getValue();

                if (user == null || expectedHits == 0 || actualHits == 0) return;

                appendDiceLuck(sb, index, user, expectedHits, actualHits);
            });

        return sb.toString();
    }

    public Map<String, Entry<Double, Integer>> getAllPlayersDiceLuck(boolean ignoreEndedGames) {
        Map<String, Entry<Double, Integer>> playerDiceLucks = new HashMap<>();
        Map<String, Set<Double>> playerAverageDiceLucks = new HashMap<>();

        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getInstance().getGamesPage(currentPage++);
            for (Game game : pagedGames.getGames().stream().filter(endedGamesFilter).toList()) {
                for (Player player : game.getPlayers().values()) {
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
        } while (pagedGames.hasNextPage());

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
                appendDiceLuck(sb, index, user, expectedHits, actualHits);
            }
        }
        return sb.toString();
    }

    private void appendDiceLuck(StringBuilder sb, AtomicInteger index, User user, double expectedHits, int actualHits) {
        double averageDiceLuck = actualHits / expectedHits;
        sb.append("`").append(Helper.leftpad(String.valueOf(index.get()), 3)).append(". ");
        sb.append(String.format("%.2f", averageDiceLuck));
        sb.append("` ").append(user.getEffectiveName());
        sb.append("   [").append(actualHits).append("/").append(String.format("%.1f", expectedHits)).append(" actual/expected]");
        sb.append("\n");
        index.getAndIncrement();
    }
}
