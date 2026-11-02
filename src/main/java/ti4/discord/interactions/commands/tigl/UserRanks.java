package ti4.discord.interactions.commands.tigl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.tigl.TiglRankHistoryService;
import ti4.website.UltimateStatisticsApiException;

class UserRanks extends Subcommand {

    private static final List<String> PLAYER_OPTIONS = List.of(
            Constants.PLAYER1,
            Constants.PLAYER2,
            Constants.PLAYER3,
            Constants.PLAYER4,
            Constants.PLAYER5,
            Constants.PLAYER6);

    UserRanks() {
        super(Constants.TIGL_USER_RANKS, "Show TIGL ranks for yourself or up to six players");
        for (String playerOption : PLAYER_OPTIONS) {
            addOptions(new OptionData(OptionType.USER, playerOption, "Player to look up (default: you)"));
        }
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.TIGL_INCLUDE_HISTORY,
                "True to also show the full rank history (default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeHistory =
                event.getOption(Constants.TIGL_INCLUDE_HISTORY, Boolean.FALSE, OptionMapping::getAsBoolean);
        List<Long> discordUserIds = resolveDiscordUserIds(event);

        String message;
        try {
            message = TiglRankHistoryService.getRankMessage(discordUserIds, includeHistory);
        } catch (UltimateStatisticsApiException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not look up TIGL ranks: " + e.getMessage());
            return;
        }

        if (includeHistory) {
            MessageHelper.sendMessageToThread(event.getChannel(), "TIGL Ranks", message);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
    }

    private static List<Long> resolveDiscordUserIds(SlashCommandInteractionEvent event) {
        Set<Long> discordUserIds = new LinkedHashSet<>();
        for (String playerOption : PLAYER_OPTIONS) {
            User user = event.getOption(playerOption, null, OptionMapping::getAsUser);
            if (user != null) {
                discordUserIds.add(user.getIdLong());
            }
        }
        if (discordUserIds.isEmpty()) {
            discordUserIds.add(event.getUser().getIdLong());
        }
        return new ArrayList<>(discordUserIds);
    }
}
