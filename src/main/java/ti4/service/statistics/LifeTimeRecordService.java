package ti4.service.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.SearchGameHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class LifeTimeRecordService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(
            new StatisticsPipeline.StatisticsEvent("getLifeTimeRecords", event, () -> getLifeTimeRecords(event)));
    }

    private void getLifeTimeRecords(SlashCommandInteractionEvent event) {
        List<User> members = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            if (!Objects.nonNull(event.getOption("player" + i))) {
                break;
            }
            User member = event.getOption("player" + i).getAsUser();
            members.add(member);
        }
        String records = DiceLuckService.getDiceLuck(members) + AverageTurnTimeService.getAverageTurnTime(members) + SearchGameHelper.getTotalCompletedNOngoingGames(members, event);

        
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), records);
    }
}
