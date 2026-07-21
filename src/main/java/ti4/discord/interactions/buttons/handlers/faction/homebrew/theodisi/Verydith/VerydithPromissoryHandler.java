package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class VerydithPromissoryHandler {
    
    public static void returnPactRenewedAtStartOfStatus(Game game) {
        for (Player holder : game.getPlayers().values()) {
            if (!holder.getPromissoryNotesInPlayArea().contains("thpnverydith")) {
                continue;
            }
    
            Player owner = game.getPNOwner("thpnverydith");
            if (owner == null || !owner.isRealPlayer()) {
                continue;
            }
    
            holder.removePromissoryNote("thpnverydith");
            owner.setPromissoryNote("thpnverydith");
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    "_Pact Renewed_ has been returned to " + owner.getRepresentationNoPing() + ".");
        }
    }
}
