package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ListTitlesGiven extends StatisticsSubcommandData {
    public ListTitlesGiven() {
        super(Constants.LIST_TITLES_GIVEN, "List the frequency with which slash commands are used");
        addOptions(new OptionData(OptionType.STRING, Constants.TITLE, "Breakdown for a specific title").setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        boolean titleOnly = false;
        String specificTitle = event.getOption(Constants.TITLE, null, OptionMapping::getAsString);
        if (specificTitle != null) {
            titleOnly = true;
        }
        Map<String, Integer> timesTitleHasBeenBestowed = new HashMap<>();
        Map<String, Integer> titlesAPersonHas = new HashMap<>();
        Map<String, Integer> timesPersonHasGottenSpecificTitle = new HashMap<>();

        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getInstance().getGamesPage(currentPage++);
            for (Game game : pagedGames.getGames()) {
                for (String storedValue : game.getMessagesThatICheckedForAllReacts().keySet()) {
                    if (storedValue.contains("TitlesFor")) {
                        String userID = storedValue.replace("TitlesFor", "");
                        for (String title : game.getStoredValue(storedValue).split("_")) {
                            timesTitleHasBeenBestowed.put(title, 1 + timesTitleHasBeenBestowed.getOrDefault(title, 0));
                            titlesAPersonHas.put(userID, 1 + titlesAPersonHas.getOrDefault(userID, 0));
                            timesPersonHasGottenSpecificTitle.put(userID + "_" + title, 1 + timesPersonHasGottenSpecificTitle.getOrDefault(userID + "_" + title, 0));
                        }
                    }
                }
            }
        } while (pagedGames.hasNextPage());

        StringBuilder longMsg = new StringBuilder("The number of each title that has been bestowed:\n");
        Map<String, Integer> sortedTitlesMapAsc = sortByValue(timesTitleHasBeenBestowed, false);
        for (String title : sortedTitlesMapAsc.keySet()) {
            longMsg.append(title).append(": ").append(sortedTitlesMapAsc.get(title)).append(" \n");
        }
        longMsg.append("\nThe number of titles each person has: \n");
        Map<String, Integer> sortedMapAscPlayers = sortByValue(titlesAPersonHas, false);
        for (String person : sortedMapAscPlayers.keySet()) {
            if (event.getGuild().getMemberById(person) == null) {
                continue;
            }
            longMsg.append(event.getGuild().getMemberById(person).getEffectiveName()).append(": ").append(sortedMapAscPlayers.get(person)).append(" \n");
        }
        if (titleOnly) {
            Map<String, Integer> sortedMapAscPlayersNTitles = sortByValue(timesPersonHasGottenSpecificTitle, false);
            longMsg.append("\nThe number of titles each person has for the title of ").append(specificTitle).append(": \n");
            for (String personNTitle : sortedMapAscPlayersNTitles.keySet()) {
                if (!personNTitle.toLowerCase().contains(specificTitle.toLowerCase())) {
                    continue;
                }
                String person = personNTitle.split("_")[0];
                if (event.getGuild().getMemberById(person) == null) {
                    continue;
                }
                longMsg.append(event.getGuild().getMemberById(person).getEffectiveName()).append(": ").append(sortedMapAscPlayersNTitles.get(personNTitle)).append(" \n");
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg.toString());
    }

    public static Map<String, Integer> sortByValue(Map<String, Integer> unsortMap, boolean order) {
        List<Entry<String, Integer>> list = new ArrayList<>(unsortMap.entrySet());

        // Sorting the list based on values
        list.sort((o1, o2) -> order ? o1.getValue().compareTo(o2.getValue()) == 0
            ? o1.getKey().compareTo(o2.getKey())
            : o1.getValue().compareTo(o2.getValue())
            : o2.getValue().compareTo(o1.getValue()) == 0
                ? o2.getKey().compareTo(o1.getKey())
                : o2.getValue().compareTo(o1.getValue()));
        return list.stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> b, LinkedHashMap::new));

    }

}
