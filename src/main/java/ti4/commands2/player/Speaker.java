package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class Speaker extends GameStateSubcommand {

    public Speaker() {
        super(Constants.SPEAKER, "Speaker selection", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        game.setSpeakerUserID(player.getUserID());
        String msg = Emojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation();
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
