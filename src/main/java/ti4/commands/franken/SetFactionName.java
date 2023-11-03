package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class SetFactionName extends FrankenSubcommandData {

    public SetFactionName() {
        super(Constants.SET_FACTION_NAME, "Set franken faction suffix");
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_FACTION_NAME, "Suffix to use, will look like frankenWhateverName.").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        if (!activeGame.isFrankenGame()) {
            sendMessage("This can only be run in Franken games.");
            return;
        }
        
        String frankenName = event.getOption(Constants.FRANKEN_FACTION_NAME, null, OptionMapping::getAsString);

        player.setFaction("franken"+frankenName);
        
    }
    
}
