package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RemoveTeamMate extends PlayerSubcommandData {
    public RemoveTeamMate() {
        super(Constants.REMOVE_TEAMMATE, "Remove a teammate");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if(addOption!= null){
            player.removeTeamMateID(addOption.getAsUser().getId());
        }
        sendMessage("Removed "+addOption.getAsUser().getAsMention() + " from "+player.getFaction()+"'s team");
    }
}
