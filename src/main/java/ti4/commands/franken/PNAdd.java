package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PNAdd extends PNAddRemove {

    public PNAdd() {
        super(Constants.PN_ADD, "Add a Promissory Note to your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs) {
        addPromissoryNotes(getEvent(), getActiveGame(), player, pnIDs);
    }

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
                sb.append(pnID).append(" (player already owned this PN)");
                sb.append("\n");
                continue;
            }
            sb.append(PromissoryNoteHelper.getPromissoryNoteRepresentation(game, pnID));
            sb.append("\n");
            player.addOwnedPromissoryNoteByID(pnID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
