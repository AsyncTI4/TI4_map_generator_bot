package ti4.commands2.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PNInfo extends GameStateSubcommand {

    public PNInfo() {
        super(Constants.INFO, "Send your Promissory Notes to your Cards Info thread", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(getGame(), getPlayer(), true, event);
        MessageHelper.sendMessageToEventChannel(event, "PN Info Sent");
    }

    @ButtonHandler("refreshPNInfo")
    public static void sendPromissoryNoteInfoLongForm(Game game, Player player) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, true);
    }
}
