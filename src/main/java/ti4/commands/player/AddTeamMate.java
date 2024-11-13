package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddTeamMate extends PlayerSubcommandData {
    public AddTeamMate() {
        super(Constants.ADD_TEAMMATE, "Add a teammate");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if (addOption != null) {
            if (player.getTeamMateIDs().contains(addOption.getAsUser().getId())) {
                MessageHelper.sendMessageToEventChannel(event, "User " + addOption.getAsUser().getAsMention() + " is already a part of " + player.getFaction() + "'s team.");
                return;
            }
            player.addTeamMateID(addOption.getAsUser().getId());
        }

        game.setCommunityMode(true);
        MessageHelper.sendMessageToEventChannel(event, "Added " + addOption.getAsUser().getAsMention() + " as part of " + player.getFaction() + "'s team.");
    }
}
