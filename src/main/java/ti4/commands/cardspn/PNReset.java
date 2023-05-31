package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PNReset extends PNCardsSubcommandData {
    public PNReset() {
        super(Constants.PN_RESET, "Reset your Promissory Notes and send to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (Mapper.isColorValid(playerColor) && Mapper.isFaction(playerFaction)) {
            List<String> promissoryNotes = new ArrayList<>(Mapper.getPromissoryNotes(activeMap, playerColor, playerFaction));
            for (String promissoryNote : promissoryNotes) {
                activeMap.removePurgedPN(promissoryNote);
            }
        }
        PNInfo.checkAndAddPNs(activeMap, player);
        String headerText = Helper.getPlayerRepresentation(player, activeMap) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        PNInfo.sendPromissoryNoteInfo(activeMap, player, true);
        sendMessage("PN Info Sent");
    }
}
