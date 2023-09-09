package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class Speaker extends PlayerSubcommandData {
    public Speaker() {
        super(Constants.SPEAKER, "Speaker selection");
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
        activeGame.setSpeaker(player.getUserID());
        String msg = Emojis.SpeakerToken + " Speaker assigned to: " + Helper.getPlayerRepresentation(player, activeGame);
        sendMessage(msg);
    }
}
