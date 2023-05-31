package ti4.commands.franken;

import java.util.List;

import ti4.commands.cardspn.PNInfo;
import ti4.commands.player.AbilityInfo;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class PNAdd extends PNAddRemove {
    public PNAdd() {
        super(Constants.PN_ADD, "Add a Promissory Note to your faction's owned notes");
    }

    @Override
    public void doAction(Player player, List<String> pnIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveMap())).append(" added PNs:\n");
        for (String pnID : pnIDs ){
            if (player.ownsPromissoryNote(pnID)) {
                sb.append("> ").append(pnID).append(" (player already owns this PN)");
            } else {
                sb.append("> ").append(PNInfo.getPromissoryNoteRepresentation(pnID));
            }
            sb.append("\n");
            player.addOwnedPromissoryNoteByID(pnID);
        }
        sendMessage(sb.toString());
    }
}
