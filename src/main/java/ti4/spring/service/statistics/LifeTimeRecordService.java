package ti4.spring.service.statistics;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Service;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticsPipeline;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class LifeTimeRecordService {

    private final DiceLuckService diceLuckService;
    private final AverageHitsPerTurnService averageHitsPerTurnService;
    private final AverageTurnTimeService averageTurnTimeService;
    private final UserGameInfoService userGameInfoService;

    public void getLifeTimeRecords(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> sendLifeTimeRecords(event));
    }

    private void sendLifeTimeRecords(SlashCommandInteractionEvent event) {
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            if (event.getOption("player" + i) == null) {
                break;
            }
            User user = event.getOption("player" + i).getAsUser();
            users.add(user);
        }

        List<String> userIds = users.stream().map(User::getId).toList();
        String records = diceLuckService.getDiceLuck(userIds)
                + averageHitsPerTurnService.getAverageHitsPerTurn(userIds)
                + averageTurnTimeService.getAverageTurnTimesString(userIds)
                + userGameInfoService.getTotalCompletedNOngoingGames(users);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), records);
    }

    public static LifeTimeRecordService getBean() {
        return SpringContext.getBean(LifeTimeRecordService.class);
    }
}
