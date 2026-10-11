package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

class Speaker extends GameStateSubcommand {

    public Speaker() {
        super(Constants.SPEAKER, "Speaker selection", true, true);
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        game.setSpeakerUserID(player.getUserID());
        String msg = MiscEmojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentationNoPing();
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
