package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class MarkFollowed extends GameStateSubcommand {

    public MarkFollowed() {
        super(Constants.MARK_FOLLOWED, "Mark player as having followed a strategy card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Initiative number for the strategy card being followed").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color following").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int sc = event.getOption(Constants.SC).getAsInt();
        player.addFollowedSC(sc);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully marked " +
            player.getRepresentation() + " as having followed **" + Helper.getSCName(sc, getGame()) + "**.");
    }

}
