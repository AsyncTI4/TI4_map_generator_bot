package ti4.service.statistics;

import java.time.LocalDate;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.SortHelper;
import ti4.message.MessageHelper;
import ti4.spring.service.usage.InteractionCountService;

@UtilityClass
public class ListSlashCommandsUsedService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> listSlashCommandsUsed(event));
    }

    private static void listSlashCommandsUsed(SlashCommandInteractionEvent event) {
        Integer lastNDays = event.getOption(Constants.LAST_N_DAYS, null, OptionMapping::getAsInt);
        LocalDate since = lastNDays == null ? null : LocalDate.now().minusDays(lastNDays);

        InteractionCountService service = InteractionCountService.get();
        long total = service.getTotalSlashCommandCount(since);
        Map<String, Long> slashCommands = service.getSlashCommandCounts(since);

        String period = lastNDays == null ? "all time" : "last " + lastNDays + " days";
        StringBuilder msg = new StringBuilder("The number of slash commands used (" + period + ") is " + total
                + ". The following is the recorded frequency of slash commands:\n");
        Map<String, Long> sorted = SortHelper.sortByLongValue(slashCommands, false);
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            msg.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), msg.toString());
    }
}
