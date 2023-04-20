package ti4.commands.explore;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicLookAtTop extends GenericRelicAction {
    public RelicLookAtTop() {
        super(Constants.RELIC_LOOK_AT_TOP, "Look at the top of the relic deck. Sends to Cards Info thread.");
    }

    public void doAction(Player player, SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        List<String> relicDeck = activeMap.getAllRelics();
        relicDeck.remove(Constants.ENIGMATIC_DEVICE);
        if (relicDeck.isEmpty()) {
            sendMessage("Relic deck is empty");
            return;
        }
        String relicID = relicDeck.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("**Relic - Look at Top**\n").append(Helper.getPlayerRepresentation(event, player)).append("\n");
        sb.append(Helper.getRelicRepresentation(relicID));
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, sb.toString());
    }
}
