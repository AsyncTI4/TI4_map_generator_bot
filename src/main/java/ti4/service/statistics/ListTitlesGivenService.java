package ti4.service.statistics;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.SortHelper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
public class ListTitlesGivenService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> listTitlesGiven(event));
    }

    private void listTitlesGiven(SlashCommandInteractionEvent event) {
        boolean titleOnly = false;
        String specificTitle = event.getOption(Constants.TITLE, null, OptionMapping::getAsString);
        if (specificTitle != null) {
            titleOnly = true;
        }

        Map<String, Integer> timesTitleHasBeenBestowed = new HashMap<>();
        Map<String, Integer> titlesAPersonHas = new HashMap<>();
        Map<String, Integer> timesPersonHasGottenSpecificTitle = new HashMap<>();

        GamesPage.consumeAllGames(game -> aggregateTitles(game, timesTitleHasBeenBestowed, titlesAPersonHas, timesPersonHasGottenSpecificTitle));

        StringBuilder longMsg = new StringBuilder("The number of each title that has been bestowed:\n");
        Map<String, Integer> sortedTitlesMapAsc = SortHelper.sortByValue(timesTitleHasBeenBestowed, false);
        for (String title : sortedTitlesMapAsc.keySet()) {
            longMsg.append(title).append(": ").append(sortedTitlesMapAsc.get(title)).append(" \n");
        }
        longMsg.append("\nThe number of titles each player has: \n");
        Map<String, Integer> sortedMapAscPlayers = SortHelper.sortByValue(titlesAPersonHas, false);
        for (String person : sortedMapAscPlayers.keySet()) {
            if (event.getGuild().getMemberById(person) == null) {
                continue;
            }
            longMsg.append(event.getGuild().getMemberById(person).getEffectiveName()).append(": ").append(sortedMapAscPlayers.get(person)).append(" \n");
        }
        if (titleOnly) {
            Map<String, Integer> sortedMapAscPlayersNTitles = SortHelper.sortByValue(timesPersonHasGottenSpecificTitle, false);
            longMsg.append("\nThe number of titles each player has for the title of ").append(specificTitle).append(": \n");
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

    private void aggregateTitles(Game game, Map<String, Integer> timesTitleHasBeenBestowed, Map<String, Integer> titlesAPersonHas, Map<String, Integer> timesPersonHasGottenSpecificTitle) {
        for (String storedValue : game.getMessagesThatICheckedForAllReacts().keySet()) {
            if (!storedValue.contains("TitlesFor")) {
                continue;
            }
            String userID = storedValue.replace("TitlesFor", "");
            for (String title : game.getStoredValue(storedValue).split("_")) {
                timesTitleHasBeenBestowed.put(title, 1 + timesTitleHasBeenBestowed.getOrDefault(title, 0));
                titlesAPersonHas.put(userID, 1 + titlesAPersonHas.getOrDefault(userID, 0));
                timesPersonHasGottenSpecificTitle.put(userID + "_" + title, 1 + timesPersonHasGottenSpecificTitle.getOrDefault(userID + "_" + title, 0));
            }
        }
    }
}
