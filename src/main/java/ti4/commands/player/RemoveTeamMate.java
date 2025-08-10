package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RemoveTeamMate extends GameStateSubcommand {

    public RemoveTeamMate() {
        super(Constants.REMOVE_TEAMMATE, "Remove a teammate", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        User targetPlayer = event.getOption(Constants.PLAYER2).getAsUser();
        player.removeTeamMateID(targetPlayer.getId());
        MessageHelper.sendMessageToEventChannel(
                event, "Removed " + targetPlayer.getAsMention() + " from " + player.getFaction() + "'s team");
    }
}
