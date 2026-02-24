package ti4.service.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.SearchGameHelper;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.AverageTurnTimeService;

@UtilityClass
public class LifeTimeRecordService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getLifeTimeRecords(event));
    }

    private void getLifeTimeRecords(SlashCommandInteractionEvent event) {
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            if (!Objects.nonNull(event.getOption("player" + i))) {
                break;
            }
            User user = event.getOption("player" + i).getAsUser();
            users.add(user);
        }
        var userIds = users.stream().map(User::getId).toList();
        String records = DiceLuckService.getDiceLuck(users)
                + AverageTurnTimeService.getBean().getAverageTurnTimesString(userIds)
                + SearchGameHelper.getTotalCompletedNOngoingGames(users, event);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), records);
    }
}
