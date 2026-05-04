package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.service.CombatReplayHouseService;
import ti4.spring.context.SpringContext;

final class LazaxCommandAuthorization {

    private static final String SEASON_ADMIN_USER_ID = "139760548471504897";

    private LazaxCommandAuthorization() {}

    static boolean isSeasonAdmin(SlashCommandInteractionEvent event) {
        return event != null && SEASON_ADMIN_USER_ID.equals(event.getUser().getId());
    }

    static boolean canUseHouseCommand(SlashCommandInteractionEvent event, CombatReplayHouse house) {
        if (isSeasonAdmin(event)) return true;
        return SpringContext.getBean(CombatReplayHouseService.class)
                        .houseForUser(event.getUser().getId())
                == house;
    }

    static boolean isInHouseChannel(SlashCommandInteractionEvent event, CombatReplayHouse house) {
        return event != null && house != null && event.getChannel().getName().equalsIgnoreCase(house.channelName());
    }
}
