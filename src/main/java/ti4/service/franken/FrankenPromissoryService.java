package ti4.service.franken;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class FrankenPromissoryService {

    public static void addPromissoryNotes(GenericInteractionCreateEvent event, Game game, Player player, List<String> pnIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added PNs:\n");
        for (String pnID : pnIDs ){
            Player pnOwner = game.getPNOwner(pnID);
            sb.append("> ");
            if (pnOwner != null) {
                sb.append(pnID).append(" is already owned by ").append(pnOwner.getUserName());
                sb.append("\n");
                continue;
            }

            if (player.ownsPromissoryNote(pnID)) {
                sb.append(pnID).append(" (player already owned this promissory note)");
                sb.append("\n");
                continue;
            }
            sb.append(PromissoryNoteHelper.getPromissoryNoteRepresentation(game, pnID));
            sb.append("\n");
            player.addOwnedPromissoryNoteByID(pnID);
            player.setPromissoryNote(pnID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static void removePromissoryNotes(GenericInteractionCreateEvent event, Player player, List<String> pnIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed PNs:\n");
        for (String pnID : pnIDs) {
            if (!player.ownsPromissoryNote(pnID)) {
                sb.append("> ").append(pnID).append(" (player did not own this promissory note)");
            } else {
                sb.append("> ").append(pnID);
            }
            sb.append("\n");
            player.removeOwnedPromissoryNoteByID(pnID);
            player.removePromissoryNote(pnID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
