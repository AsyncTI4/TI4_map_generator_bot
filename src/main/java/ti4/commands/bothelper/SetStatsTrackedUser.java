package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SetStatsTrackedUser extends GameStateSubcommand {

    SetStatsTrackedUser() {
        super(Constants.SET_STATS_TRACKED_USER, "Set stats tracking user for a player.", true, false);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Player faction or color")
                        .setRequired(true)
                        .setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.USER, "User to track stats for").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player not found.");
            return;
        }

        User trackedUser = event.getOption(Constants.USER).getAsUser();
        Member member = event.getGuild() == null ? null : event.getGuild().getMember(trackedUser);
        String trackedUserName = member == null ? trackedUser.getName() : member.getEffectiveName();
        trackedUserName = StringUtils.defaultIfBlank(trackedUserName, trackedUser.getName());

        player.setStatsTrackedUserID(trackedUser.getId());
        player.setStatsTrackedUserName(trackedUserName);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Updated stats tracking for " + player.getUserName() + " to " + trackedUserName + ".");
    }
}
