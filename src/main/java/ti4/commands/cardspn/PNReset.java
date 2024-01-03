package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class PNReset extends PNCardsSubcommandData {
    public PNReset() {
        super(Constants.PN_RESET, "Reset your Promissory Notes and send to your Cards Info thread");
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
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (Mapper.isValidColor(playerColor) && Mapper.isValidFaction(playerFaction)) {
            List<String> promissoryNotes = new ArrayList<>(Mapper.getColorPromissoryNoteIDs(activeGame, playerColor));
            for (String promissoryNote : promissoryNotes) {
                activeGame.removePurgedPN(promissoryNote);
            }
        }
        PNInfo.checkAndAddPNs(activeGame, player);
        PNInfo.sendPromissoryNoteInfo(activeGame, player, true, event);
        sendMessage("PN Info Sent");
    }
}
