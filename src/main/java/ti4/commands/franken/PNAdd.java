package ti4.commands.franken;

import java.util.List;

import ti4.commands.cardspn.PNInfo;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PNAdd extends PNAddRemove {
    public PNAdd() {
        super(Constants.PN_ADD, "Add a Promissory Note to your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added PNs:\n");
        for (String pnID : pnIDs ){
            Player pnOwner = getActiveGame().getPNOwner(pnID);
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
            sb.append(PNInfo.getPromissoryNoteRepresentation(getActiveGame(), pnID));
            sb.append("\n");
            player.addOwnedPromissoryNoteByID(pnID);
        }
        MessageHelper.sendMessageToEventChannel(getEvent(), sb.toString());
    }
}
