package ti4.commands2.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

class HeroUnplay extends GameStateSubcommand {

    public HeroUnplay() {
        super(Constants.INACTIVE_LEADER, "Set leader as inactive", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        Player player = getPlayer();
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        if (playerLeader != null) {
            playerLeader.setActive(false);
            MessageHelper.sendMessageToEventChannel(event, "Leader deactivated/unplayed");
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
        }
    }
}
