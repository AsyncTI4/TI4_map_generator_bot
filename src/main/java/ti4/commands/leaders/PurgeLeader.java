package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PurgeLeader extends GameStateSubcommand {

    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        Player player = getPlayer();
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToEventChannel(event, Emojis.getFactionLeaderEmoji(playerLeader));
            String message = player.getRepresentation() +
                " purged " + Helper.getLeaderShortRepresentation(playerLeader);
            MessageHelper.sendMessageToEventChannel(event, message);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
        }
    }
}
