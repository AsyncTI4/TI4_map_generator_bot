package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Speaker extends PlayerSubcommandData {
    public Speaker() {
        super(Constants.SPEAKER, "Speaker selection");
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
        game.setSpeakerUserID(player.getUserID());
        String msg = Emojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation();
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
