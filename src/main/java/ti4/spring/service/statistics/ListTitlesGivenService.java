package ti4.spring.service.statistics;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.SortHelper;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.TitleEntity;
import ti4.spring.service.persistence.TitleEntityRepository;

@Service
@RequiredArgsConstructor
public class ListTitlesGivenService {

    private static final int MAX_PLAYERS_TO_SHOW = 25;

    private final TitleEntityRepository titleEntityRepository;

    @Transactional(readOnly = true)
    public void listTitlesGiven(SlashCommandInteractionEvent event) {
        Map<String, Integer> timesTitleHasBeenBestowed = new HashMap<>();
        Map<String, Integer> titlesAPersonHas = new HashMap<>();
        Map<String, Integer> timesPersonHasGottenSpecificTitle = new HashMap<>();
        Map<String, String> userNamesById = new HashMap<>();

        for (TitleEntity titleEntity : titleEntityRepository.findAllWithUsers()) {
            String title = titleEntity.getTitle();
            String userId = titleEntity.getUser().getId();
            String userName = titleEntity.getUser().getName();

            timesTitleHasBeenBestowed.merge(title, 1, Integer::sum);
            titlesAPersonHas.merge(userId, 1, Integer::sum);
            String userAndTitle = userId + "_" + title;
            timesPersonHasGottenSpecificTitle.merge(userAndTitle, 1, Integer::sum);
            userNamesById.put(userId, userName);
        }

        StringBuilder longMsg = new StringBuilder("The number of each title that has been bestowed:\n");
        Map<String, Integer> sortedTitlesMapAsc = SortHelper.sortByValue(timesTitleHasBeenBestowed, false);
        for (Map.Entry<String, Integer> entry : sortedTitlesMapAsc.entrySet()) {
            longMsg.append(entry.getKey()).append(": ").append(entry.getValue()).append(" \n");
        }

        longMsg.append("\nThe number of titles each player has: \n");
        Map<String, Integer> sortedMapAscPlayers = SortHelper.sortByValue(titlesAPersonHas, false);
        int playersShown = 0;
        for (Map.Entry<String, Integer> entry : sortedMapAscPlayers.entrySet()) {
            if (playersShown >= MAX_PLAYERS_TO_SHOW) {
                break;
            }
            String person = userNamesById.get(entry.getKey());
            if (person == null) {
                continue;
            }
            longMsg.append(person).append(": ").append(entry.getValue()).append(" \n");
            playersShown++;
        }

        String specificTitle = event.getOption(Constants.TITLE, null, OptionMapping::getAsString);
        if (specificTitle != null) {
            Map<String, Integer> sortedMapAscPlayersNTitles =
                    SortHelper.sortByValue(timesPersonHasGottenSpecificTitle, false);
            longMsg.append("\nThe number of titles each player has for the title of ")
                    .append(specificTitle)
                    .append(": \n");
            int playersShownForSpecificTitle = 0;
            for (Map.Entry<String, Integer> entry : sortedMapAscPlayersNTitles.entrySet()) {
                if (playersShownForSpecificTitle >= MAX_PLAYERS_TO_SHOW) {
                    break;
                }
                String[] personAndTitle = entry.getKey().split("_", 2);
                if (personAndTitle.length < 2 || !personAndTitle[1].equalsIgnoreCase(specificTitle)) {
                    continue;
                }
                String person = userNamesById.get(personAndTitle[0]);
                if (person == null) {
                    continue;
                }
                longMsg.append(person).append(": ").append(entry.getValue()).append(" \n");
                playersShownForSpecificTitle++;
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg.toString());
    }

    public static ListTitlesGivenService getBean() {
        return SpringContext.getBean(ListTitlesGivenService.class);
    }
}
