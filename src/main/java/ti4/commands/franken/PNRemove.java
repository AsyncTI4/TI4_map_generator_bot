package ti4.commands.franken;

import java.util.List;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class PNRemove extends PNAddRemove {
    public PNRemove() {
        super(Constants.PN_REMOVE, "Remove an Promissory Note from your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveMap())).append(" removed PNs:\n");
        for (String pnID : pnIDs) {
            if (!player.ownsPromissoryNote(pnID)) {
                sb.append("> ").append(pnID).append(" (player did not own this PN)");
            } else {
                sb.append("> ").append(pnID);
            }
            sb.append("\n");
            player.removeOwnedPromissoryNoteByID(pnID);
        }
        sendMessage(sb.toString());
    }
}
