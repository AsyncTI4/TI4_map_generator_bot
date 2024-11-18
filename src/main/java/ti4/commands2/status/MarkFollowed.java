package ti4.commands2.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class MarkFollowed extends GameStateSubcommand {

    public MarkFollowed() {
        super(Constants.MARK_FOLLOWED, "Mark player as having followed an SC", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "SC player followed").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int sc = event.getOption(Constants.SC).getAsInt();
        player.addFollowedSC(sc);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully marked " + player.getRepresentation() + " as having followed SC #" + sc);
    }

}
